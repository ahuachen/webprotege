package edu.stanford.bmir.protege.web.client.ui.ontology.properties;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtext.client.core.EventObject;
import com.gwtext.client.data.Node;
import com.gwtext.client.widgets.tree.DefaultSelectionModel;
import com.gwtext.client.widgets.tree.TreeNode;
import com.gwtext.client.widgets.tree.TreePanel;
import com.gwtext.client.widgets.tree.TreeSelectionModel;
import com.gwtext.client.widgets.tree.event.DefaultSelectionModelListenerAdapter;
import com.gwtext.client.widgets.tree.event.TreePanelListenerAdapter;
import edu.stanford.bmir.protege.web.client.LoggedInUserProvider;
import edu.stanford.bmir.protege.web.client.dispatch.DispatchServiceCallback;
import edu.stanford.bmir.protege.web.client.dispatch.DispatchServiceManager;
import edu.stanford.bmir.protege.web.client.dispatch.actions.*;
import edu.stanford.bmir.protege.web.client.permissions.LoggedInUserProjectPermissionChecker;
import edu.stanford.bmir.protege.web.client.portlet.AbstractWebProtegePortlet;
import edu.stanford.bmir.protege.web.client.portlet.LegacyCompatUtil;
import edu.stanford.bmir.protege.web.client.portlet.PortletAction;
import edu.stanford.bmir.protege.web.client.portlet.PortletActionHandler;
import edu.stanford.bmir.protege.web.client.rpc.OntologyServiceManager;
import edu.stanford.bmir.protege.web.client.rpc.data.EntityData;
import edu.stanford.bmir.protege.web.client.rpc.data.PropertyEntityData;
import edu.stanford.bmir.protege.web.client.rpc.data.PropertyType;
import edu.stanford.bmir.protege.web.client.ui.library.dlg.WebProtegeDialog;
import edu.stanford.bmir.protege.web.client.ui.library.msgbox.InputBox;
import edu.stanford.bmir.protege.web.client.ui.library.msgbox.InputBoxHandler;
import edu.stanford.bmir.protege.web.client.ui.library.msgbox.MessageBox;
import edu.stanford.bmir.protege.web.client.ui.library.msgbox.YesNoHandler;
import edu.stanford.bmir.protege.web.client.ui.library.popupmenu.PopupMenu;
import edu.stanford.bmir.protege.web.client.ui.ontology.entity.CreateEntityDialogController;
import edu.stanford.bmir.protege.web.client.ui.ontology.entity.CreateEntityInfo;
import edu.stanford.bmir.protege.web.resources.WebProtegeClientBundle;
import edu.stanford.bmir.protege.web.shared.entity.OWLEntityData;
import edu.stanford.bmir.protege.web.shared.event.BrowserTextChangedEvent;
import edu.stanford.bmir.protege.web.shared.event.BrowserTextChangedHandler;
import edu.stanford.bmir.protege.web.shared.hierarchy.*;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.renderer.GetEntityDataAction;
import edu.stanford.bmir.protege.web.shared.renderer.GetEntityDataResult;
import edu.stanford.bmir.protege.web.shared.selection.SelectionModel;
import org.semanticweb.owlapi.model.*;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static edu.stanford.bmir.protege.web.resources.WebProtegeClientBundle.BUNDLE;

// TODO: add action descriptions and labels in the config similar to the ClassTreePortlet
public class PropertiesTreePortlet extends AbstractWebProtegePortlet {

    public static final String ANNOTATION_PROPERTIES_ROOT_NAME = "Annotation properties";

    public static final String ANNOTATION_PROPERTIES_TREE_NODE_ID = "Annotation properties";

    private final DispatchServiceManager dispatchServiceManager;

    private final LoggedInUserProvider loggedInUserProvider;

    private final PortletAction createAction = new PortletAction("Create", new PortletActionHandler() {
        @Override
        public void handleActionInvoked(PortletAction action, ClickEvent event) {
            onCreateProperty();
        }
    });

    private final PortletAction deleteAction = new PortletAction("Delete", new PortletActionHandler() {
        @Override
        public void handleActionInvoked(PortletAction action, ClickEvent event) {
            onDeleteProperty();
        }
    });

    protected TreePanel treePanel;

    private final LoggedInUserProjectPermissionChecker permissionChecker;

    @Inject
    public PropertiesTreePortlet(SelectionModel selectionModel, EventBus eventBus, DispatchServiceManager dispatchServiceManager, LoggedInUserProvider loggedInUserProvider, ProjectId projectId, LoggedInUserProjectPermissionChecker permissionChecker) {
        super(selectionModel, eventBus, loggedInUserProvider, projectId);
        this.dispatchServiceManager = dispatchServiceManager;
        this.loggedInUserProvider = loggedInUserProvider;
        this.permissionChecker = permissionChecker;

        setTitle("Properties Tree");

        treePanel = new TreePanel();
        treePanel.setAnimate(true);

        treePanel.addListener(new TreePanelListenerAdapter() {
            @Override
            public void onClick(TreeNode node, EventObject e) {
                selectNode(node);
            }

            @Override
            public void onExpandNode(TreeNode node) {
                getSubProperties(node.getId(), true);
            }

            @Override
            public void onContextMenu(TreeNode node, EventObject e) {
                PopupMenu contextMenu = new PopupMenu();
                contextMenu.addItem("Show IRI", new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        Optional<OWLEntity> selectedEntity = getSelectedEntity();
                        if (selectedEntity.isPresent()) {
                            String iri = selectedEntity.get().getIRI().toQuotedString();
                            InputBox.showOkDialog("Property IRI", true, iri, new InputBoxHandler() {
                                @Override
                                public void handleAcceptInput(String input) {

                                }
                            });
                        }
                    }
                });
                contextMenu.addItem("Show direct link", new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        String location = Window.Location.getHref();
                        InputBox.showOkDialog("Direct link", true, location, new InputBoxHandler() {
                            @Override
                            public void handleAcceptInput(String input) {

                            }
                        });
                    }
                });
                contextMenu.addSeparator();
                contextMenu.addItem("Refresh tree", new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        onRefresh();
                    }
                });
                contextMenu.show(e.getXY()[0], e.getXY()[1] + 5);
            }
        });


        // Temporary - to be replaced when ontology is loaded.
        TreeNode root = new TreeNode(null);
        root.setId("RootPropertyNode");
        root.setHref("");
        root.setUserObject(new PropertyEntityData("RootPropertyNode", "RootPropertyNode", null));
        setTreeNodeIcon(root);

        treePanel.setRootNode(root);
        treePanel.setRootVisible(false);

        addToolbarButtons();
        addPortletAction(createAction);
        addPortletAction(deleteAction);
        getContentHolder().setWidget(new ScrollPanel(treePanel.asWidget()));


        addProjectEventHandler(ObjectPropertyHierarchyParentAddedEvent.TYPE, new ObjectPropertyHierarchyParentAddedHandler() {
            @Override
            public void handleObjectPropertyHierarchyParentAdded(ObjectPropertyHierarchyParentAddedEvent event) {
                handleRelationshipAdded(event);
            }
        });

        addProjectEventHandler(ObjectPropertyHierarchyParentRemovedEvent.TYPE, new ObjectPropertyHierarchyParentRemovedHandler() {
            @Override
            public void handleObjectPropertyHierarchyParentRemoved(ObjectPropertyHierarchyParentRemovedEvent event) {
                handleRelationshipRemoved(event);
            }
        });

        addProjectEventHandler(DataPropertyHierarchyParentAddedEvent.TYPE, new DataPropertyHierarchyParentAddedHandler() {
            @Override
            public void handleDataPropertyParentAdded(DataPropertyHierarchyParentAddedEvent event) {
                handleRelationshipAdded(event);
            }
        });

        addProjectEventHandler(AnnotationPropertyHierarchyParentAddedEvent.TYPE, new AnnotationPropertyHierarchyParentAddedHandler() {
            @Override
            public void handleAnnotationPropertyHierarchyParentAdded(AnnotationPropertyHierarchyParentAddedEvent event) {
                handleRelationshipAdded(event);
            }
        });

        addProjectEventHandler(HierarchyRootAddedEvent.TYPE, new HierarchyRootAddedHandler<Serializable>() {
            @Override
            public void handleHierarchyRootAdded(HierarchyRootAddedEvent<Serializable> event) {
                handleRootAdded(event);
            }
        });

        addProjectEventHandler(HierarchyRootRemovedEvent.TYPE, new HierarchyRootRemovedHandler<Serializable>() {
            @Override
            public void handleHierarchyRootRemoved(HierarchyRootRemovedEvent<Serializable> event) {
                handleRootRemoved(event);
            }
        });

        addProjectEventHandler(BrowserTextChangedEvent.TYPE, new BrowserTextChangedHandler() {
            @Override
            public void browserTextChanged(BrowserTextChangedEvent event) {
                handleBrowserTextChanged(event);
            }
        });
        DefaultSelectionModel selModel = new DefaultSelectionModel();
        treePanel.setSelectionModel(selModel);
        selModel.addSelectionModelListener(new DefaultSelectionModelListenerAdapter() {
            @Override
            public void onSelectionChange(DefaultSelectionModel defaultSelectionModel, TreeNode treeNode) {
                if (treeNode != null) {
                    Optional<OWLEntityData> sel = getEntityDataFromTreeNode(treeNode);
                    if (sel.isPresent()) {
                        getSelectionModel().setSelection(sel.get().getEntity());
                    }
                }
            }
        });
        updateButtonStates();
    }

    private void onRefresh() {
        treePanel.setVisible(false);
        TreeNode rootNode = treePanel.getRootNode();
        rootNode.collapse();
        for (Node child : rootNode.getChildNodes()) {
            rootNode.removeChild(child);
        }
        rootNode.expand();
        getSubProperties(rootNode.getId(), true);
        treePanel.setVisible(true);
        updateButtonStates();
    }

    private Optional<OWLEntityData> getSelectedTreeNodeEntityData() {
        TreeSelectionModel tsm = treePanel.getSelectionModel();
        if (tsm instanceof DefaultSelectionModel) {
            TreeNode selectedTreeNode = ((DefaultSelectionModel) tsm).getSelectedNode();
            return getEntityDataFromTreeNode(selectedTreeNode);
        }
        else {
            return Optional.absent();
        }
    }

    private static Optional<OWLEntityData> getEntityDataFromTreeNode(TreeNode selectedTreeNode) {
        if (selectedTreeNode == null) {
            return Optional.absent();
        }
        if (ANNOTATION_PROPERTIES_ROOT_NAME.equals(selectedTreeNode.getText())) {
            return Optional.absent();
        }
        Object userObject = selectedTreeNode.getUserObject();
        if (userObject instanceof EntityData) {
            return LegacyCompatUtil.toOWLEntityData((EntityData) userObject);
        }
        else {
            return Optional.absent();
        }
    }

    private void selectNode(TreeNode node) {
//        lastSelectedTreeNode = node;
        treePanel.getSelectionModel().select(node);
        Optional<OWLEntityData> sel = getSelectedTreeNodeEntityData();
        if (sel.isPresent()) {
            getSelectionModel().setSelection(sel.get().getEntity());
        }
    }

    private void handleRelationshipAdded(final HierarchyChangedEvent<? extends OWLEntity, ?> event) {
        TreeNode parentTn = findTreeNode(event.getParent());
        if (parentTn == null) {
            return;
        }
        final OWLEntity child = event.getChild();
        handleChildAdded(parentTn, child, false);
    }

    private void handleRelationshipRemoved(final HierarchyChangedEvent<? extends OWLEntity, ?> event) {
        if (event.getChild().isBuiltIn()) {
            // Don't remove built in things from the tree
            return;
        }
        TreeNode tn = findTreeNode(event.getChild());
        if (tn == null) {
            return;
        }
        TreeNode parTn = findTreeNode(event.getParent());
        if (parTn != null) {
            parTn.removeChild(tn);
        }
    }

    private void handleRootAdded(HierarchyRootAddedEvent<?> event) {
        Object root = event.getRoot();
        if (root instanceof OWLAnnotationProperty) {
            TreeNode treeNode = getAnnotationPropertiesRootNode();
            if (treeNode != null) {
                handleChildAdded(treeNode, (OWLAnnotationProperty) root, false);
            }
        }
    }

    private void handleRootRemoved(HierarchyRootRemovedEvent<?> event) {
        Object root = event.getRoot();
        if (root instanceof OWLEntity) {
            if (((OWLEntity) root).isBuiltIn()) {
                // Don't remove built in things!
                return;
            }
        }
        if (root instanceof OWLAnnotationProperty) {
            TreeNode annoRoot = getAnnotationPropertiesRootNode();
            if (annoRoot != null) {
                TreeNode childTn = findTreeNode((OWLAnnotationProperty) root);
                annoRoot.removeChild(childTn);
                childTn.remove();
            }
        }
    }


    private void handleChildAdded(TreeNode parentTn, final OWLEntity child, boolean shouldSelect) {
        final TreeNode childTn = findTreeNode(child.getIRI().toString());
        final TreeNode freshChild;
        if (childTn == null) {
            final PropertyEntityData entityData = createEntityDataPlaceholder(child);
            freshChild = createTreeNode(entityData);
            updateTextAsync(child, freshChild);
            parentTn.appendChild(freshChild);

        }
        else {
            freshChild = childTn;
        }
        if (freshChild != null && shouldSelect) {
            parentTn.expand();
            freshChild.select();
            selectNode(freshChild);
        }
    }


    private boolean isAnnotationPropertiesRootSelected() {
        TreeNode treeNode = ((DefaultSelectionModel) treePanel.getSelectionModel()).getSelectedNode();
        return treeNode != null && ANNOTATION_PROPERTIES_ROOT_NAME.equals(treeNode.getText());
    }


    private void handleBrowserTextChanged(BrowserTextChangedEvent event) {
        OWLEntity entity = event.getEntity();
        TreeNode treeNode = findTreeNode(entity);
        if (treeNode != null) {
            final Object userObject = treeNode.getUserObject();
            if (userObject instanceof EntityData) {
                EntityData entityData = (EntityData) userObject;
                entityData.setBrowserText(event.getNewBrowserText());
                treeNode.setText(getNodeText(entityData));
            }

        }
    }


    protected void insertNodeInTree(TreeNode parentNode, EntityData child) {
        TreeNode treeNode = createTreeNode(child);
        parentNode.appendChild(treeNode);
    }


    protected void onPropertyDeleted(EntityData entity) {
        TreeNode propNode = findTreeNode(entity.getName());
        if (propNode != null) {
            Node parentNode = propNode.getParentNode();
            if (parentNode != null) {
                parentNode.removeChild(propNode);
            }
            else {
                propNode.remove();
            }
        }
    }


    private boolean hasChild(TreeNode parentNode, String childId) {
        return getDirectChild(parentNode, childId) != null;
    }

    protected void addToolbarButtons() {
//        createButton = new Button("Create");
//        createButton.setCls("toolbar-button");
//        createButton.addListener(new ButtonListenerAdapter() {
//            @Override
//            public void onClick(Button button, EventObject e) {
//                onCreateProperty();
//            }
//        });
//
//
//        deleteButton = new Button("Delete");
//        // deleteButton.setIconCls("protege-class-delete-icon");
//        deleteButton.setCls("toolbar-button");
//        deleteButton.addListener(new ButtonListenerAdapter() {
//            @Override
//            public void onClick(Button button, EventObject e) {
//                onDeleteProperty();
//            }
//        });
//        setTopToolbar(new Button[]{createButton, deleteButton});
    }

    /**
     * Gets the {@link org.semanticweb.owlapi.model.EntityType} of the selected entity.
     *
     * @return The {@link org.semanticweb.owlapi.model.EntityType} of the selected entity.  Not {@code null}.
     */
    private Optional<EntityType<?>> getSelectedEntityType() {
        if (isAnnotationPropertiesRootSelected()) {
            return Optional.<EntityType<?>>of(EntityType.ANNOTATION_PROPERTY);
        }
        Optional<OWLEntity> selectedEntity = getSelectedEntity();
        if (!selectedEntity.isPresent()) {
            return Optional.absent();
        }
        else {
            return Optional.<EntityType<?>>of(selectedEntity.get().getEntityType());
        }
    }


    private void onCreateProperty() {
        Optional<EntityType<?>> selectedEntityType = getSelectedEntityType();
        if (!selectedEntityType.isPresent()) {
            return;
        }
        WebProtegeDialog.showDialog(new CreateEntityDialogController(selectedEntityType.get(), new CreateEntityDialogController.CreateEntityHandler() {
            @Override
            public void handleCreateEntity(CreateEntityInfo createEntityInfo) {
                handleCreateProperty(createEntityInfo);
            }
        }));
    }

    private void handleCreateProperty(final CreateEntityInfo createEntityInfo) {
        Optional<OWLEntity> sel = getSelectedEntity();
        GWT.log("[PropertiesTreePortlet] Creating entity.  Current selection: " + sel);
        if (!sel.isPresent()) {
            // Temp hack
            if (isAnnotationPropertiesRootSelected()) {
                createSubProperties(Optional.<OWLAnnotationProperty>absent(), createEntityInfo);
            }
            return;
        }

        if (isAnnotationPropertiesRootSelected()) {
            createSubProperties(Optional.<OWLAnnotationProperty>absent(), createEntityInfo);
            return;
        }

        OWLEntity selectedEntity = sel.get();
        selectedEntity.accept(new OWLEntityVisitor() {
            @Override
            public void visit(OWLObjectProperty property) {
                createSubProperties(property, createEntityInfo);
            }

            @Override
            public void visit(OWLDataProperty property) {
                createSubProperties(property, createEntityInfo);
            }

            @Override
            public void visit(OWLAnnotationProperty property) {
                createSubProperties(Optional.of(property), createEntityInfo);
            }

            @Override
            public void visit(OWLClass owlClass) {
            }

            @Override
            public void visit(OWLNamedIndividual individual) {
            }

            @Override
            public void visit(OWLDatatype datatype) {
            }
        });
    }

    private TreeNode getAnnotationPropertiesRootNode() {
        return treePanel.getNodeById(ANNOTATION_PROPERTIES_TREE_NODE_ID);
    }

    private void createSubProperties(Optional<OWLAnnotationProperty> parent, CreateEntityInfo createEntityInfo) {
        CreateAnnotationPropertiesAction action = new CreateAnnotationPropertiesAction(getProjectId(), createEntityInfo.getBrowserTexts(), parent);
        createSubProperties(action);
    }

    private void createSubProperties(OWLDataProperty property, CreateEntityInfo createEntityInfo) {
        CreateDataPropertiesAction action = new CreateDataPropertiesAction(getProjectId(), createEntityInfo.getBrowserTexts(), Optional.of(property));
        createSubProperties(action);
    }

    private void createSubProperties(OWLObjectProperty property, CreateEntityInfo createEntityInfo) {
        CreateObjectPropertiesAction action = new CreateObjectPropertiesAction(getProjectId(), createEntityInfo.getBrowserTexts(), Optional.of(property));
        createSubProperties(action);
    }

    private <R extends AbstractCreateEntityInHierarchyResult<E>, E extends OWLEntity> void createSubProperties(AbstractCreateEntityInHierarchyAction<R, E> action) {
        dispatchServiceManager.execute(action, new DispatchServiceCallback<R>() {
            @Override
            protected String getErrorMessage(Throwable throwable) {
                return "There was a problem creating the properties";
            }

            @Override
            public void handleSuccess(R result) {
                handleCreateEntitiesResult(result);
            }
        });
    }

    private void handleCreateEntitiesResult(AbstractCreateEntityInHierarchyResult<? extends OWLEntity> result) {
        TreeNode parentNode;
        if (result.getParent().isPresent()) {
            parentNode = this.findTreeNode(result.getParent().get());
        }
        else {
            parentNode = getAnnotationPropertiesRootNode();
        }
        if (parentNode != null) {
            handleChildAdded(parentNode, result.getEntities().iterator().next(), true);
        }
    }


    protected void onDeleteProperty() {

        Optional<OWLEntity> selection = getSelectedEntity();

        if (!selection.isPresent()) {
            return;
        }

        final OWLEntity entity = selection.get();

        if (!(entity instanceof OWLProperty)) {
            // Better safe than sorry
            return;
        }

        MessageBox.showYesNoConfirmBox("Delete selected property?", "Are you sure you want to delete the selected property ?", new YesNoHandler() {
            @Override
            public void handleYes() {
                deleteProperty((OWLProperty) entity);
            }

            @Override
            public void handleNo() {

            }
        });
    }


    protected void deleteProperty(OWLProperty propertyEntity) {
        GWT.log("Should delete property with name: " + propertyEntity, null);
        if (propertyEntity == null) {
            return;
        }
        dispatchServiceManager.execute(new DeleteEntityAction(propertyEntity, getProjectId()), new DeletePropertyHandler(propertyEntity));
    }

    private TreeNode getDirectChild(TreeNode parentNode, String childId) {
        Node[] children = parentNode.getChildNodes();
        for (Node child : children) {
            if (getNodeClsName(child).equals(childId)) {
                return (TreeNode) child;
            }
        }
        return null;
    }

    public String getNodeClsName(Node node) {
        EntityData data = (EntityData) node.getUserObject();
        return data.getName();
    }

    protected TreeNode findTreeNode(OWLEntity entity) {
        return findTreeNode(entity.getIRI().toString());
    }

    protected TreeNode findTreeNode(String id) {
        TreeNode root = treePanel.getRootNode();
        TreeNode node = findTreeNode(root, id, new ArrayList<TreeNode>());
        return node;
    }

    protected TreeNode findTreeNode(TreeNode node, String id, ArrayList<TreeNode> visited) {
        if (getNodeClsName(node).equals(id)) {
            return node;
        }
        else {
            visited.add(node);
            Node[] children = node.getChildNodes();
            for (Node element2 : children) {
                TreeNode n = findTreeNode((TreeNode) element2, id, visited);
                if (n != null) {
                    return n;
                }
            }
            return null;
        }
    }

    @Override
    public void handlePermissionsChanged() {
        updateButtonStates();
    }

    public void updateButtonStates() {
        createAction.setEnabled(false);
        deleteAction.setEnabled(false);
        permissionChecker.hasWritePermission(new DispatchServiceCallback<Boolean>() {
            @Override
            public void handleSuccess(Boolean hasPermission) {
                createAction.setEnabled(hasPermission);
                deleteAction.setEnabled(hasPermission);
            }
        });
    }

    public void setTreeNodeIcon(TreeNode node) {
        PropertyEntityData entityData = (PropertyEntityData) node.getUserObject();
        PropertyType type = entityData.getPropertyType();
        if (type == PropertyType.OBJECT) {
            node.setIcon(BUNDLE.svgPropertyIcon().getSafeUri().asString());
        }
        else if (type == PropertyType.DATATYPE) {
            node.setIcon(BUNDLE.svgPropertyIcon().getSafeUri().asString());
        }
        else if (type == PropertyType.ANNOTATION) {
            node.setIcon(BUNDLE.svgAnnotationPropertyIcon().getSafeUri().asString());
        }
        else {
            node.setIconCls(BUNDLE.style().objectPropertyIcon());
        }
    }

    public void getSubProperties(final String propName, final boolean getSubpropertiesOfSubproperties) {
        OntologyServiceManager.getInstance().getSubproperties(getProjectId(), propName, new GetSubproperties(propName, getSubpropertiesOfSubproperties));
    }

    protected TreeNode createTreeNode(EntityData entityData) {
        TreeNode node = new TreeNode(entityData.getBrowserText());
        node.setId(entityData.getName());
        node.setHref(null);
        node.setUserObject(entityData);
        setTreeNodeIcon(node);

        String text = getNodeText(entityData);
        node.setText(text);

        return node;
    }

    private String getNodeText(EntityData entityData) {
        String text = entityData.getBrowserText();
        int localAnnotationsCount = entityData.getLocalAnnotationsCount();
        if (localAnnotationsCount > 0) {
            text = text + "<img src=\"" + WebProtegeClientBundle.BUNDLE.commentSmallFilledIcon().getSafeUri().asString() + "\" />" + " " + localAnnotationsCount;
        }
        int childrenAnnotationsCount = entityData.getChildrenAnnotationsCount();
        if (childrenAnnotationsCount > 0) {
            text = text + " chd: " + childrenAnnotationsCount;
        }
        return text;
    }

    /*
     * Remote procedure calls
     */
    class GetSubproperties implements AsyncCallback<List<EntityData>> {

        private String propName;

        private boolean getSubpropertiesOfSubproperties;

        public GetSubproperties(String className, boolean getSubpropertiesOfSubproperties) {
            super();
            this.propName = className;
            this.getSubpropertiesOfSubproperties = getSubpropertiesOfSubproperties;
        }

        @Override
        public void onFailure(Throwable caught) {
            GWT.log("RPC error at getting subproperties of " + propName, caught);
        }

        @Override
        public void onSuccess(List<EntityData> children) {
            if (propName == null) {
                propName = "RootPropertyNode";
            }
            TreeNode parentNode = treePanel.getNodeById(propName);

            if (parentNode == null) {
                GWT.log("Cannot find node: " + propName, null);
                return;
            }

            // already loaded?
            // TODO - not a very good method
            if (parentNode.getFirstChild() != null) {
                // children are already fetched? Maybe?
                // TODO: think about this situation

                if (getSubpropertiesOfSubproperties) {
                    for (Object element2 : children) {
                        EntityData child = (EntityData) element2;
                        getSubProperties(child.getName(), false);
                    }
                }
                return;
            }

            for (Object element2 : children) {
                EntityData childData = (EntityData) element2;

                // if (!hasChild(clsName, childName)) {
                parentNode.appendChild(createTreeNode(childData));
                // }
            }

            if (getSubpropertiesOfSubproperties) {
                for (Object element2 : children) {
                    EntityData childName = (EntityData) element2;
                    getSubProperties(childName.getName(), false);
                }
            }

            handleAfterSetEntity(getSelectedEntity());
        }

        private boolean hasChild(String parentId, String childId) {
            TreeNode parentNode = treePanel.getNodeById(propName);

            Node[] children = parentNode.getChildNodes();

            for (Node child : children) {
                if (child.getId().equals(childId)) {
                    return true;
                }
            }
            return false;
        }
    }

    class DeletePropertyHandler extends DispatchServiceCallback<DeleteEntityResult> {

        private final OWLEntity entity;

        public DeletePropertyHandler(OWLEntity entity) {
            this.entity = entity;
        }

        @Override
        protected String getErrorMessage(Throwable throwable) {
            return "Error deleting property";
        }

        @Override
        public void handleSuccess(DeleteEntityResult result) {
            GWT.log("Delete successfully property ", null);
            onPropertyDeleted(new EntityData(entity.toStringID()));
        }
    }

    private void updateTextAsync(final OWLEntity entity, final TreeNode node) {
        dispatchServiceManager.execute(new GetEntityDataAction(getProjectId(), ImmutableSet.<OWLEntity>of(entity)), new DispatchServiceCallback<GetEntityDataResult>() {
            @Override
            public void handleSuccess(GetEntityDataResult result) {
                node.setText(result.getEntityDataMap().get(entity).getBrowserText());
            }
        });
    }

    private PropertyEntityData createEntityDataPlaceholder(OWLEntity child) {
        String browserText = "";
        final PropertyEntityData entityData = new PropertyEntityData(child.getIRI().toString(), browserText, Collections.<EntityData>emptySet());
        final EntityType<?> entityType = child.getEntityType();
        if (entityType == EntityType.OBJECT_PROPERTY) {
            entityData.setPropertyType(PropertyType.OBJECT);
        }
        else if (entityType == EntityType.DATA_PROPERTY) {
            entityData.setPropertyType(PropertyType.DATATYPE);
        }
        else {
            entityData.setPropertyType(PropertyType.ANNOTATION);
        }
        return entityData;
    }


}
