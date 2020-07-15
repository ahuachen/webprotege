package edu.stanford.bmir.protege.web.server.shortform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.ImmutableIntArray;
import edu.stanford.bmir.protege.web.shared.shortform.DictionaryLanguage;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.semanticweb.owlapi.model.OWLEntity;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 2020-07-13
 */
public class LuceneShortFormMatcher {

    @Nonnull
    private final LuceneSearchStringTokenizer searchStringTokenizer;

    @Nonnull
    private final DictionaryLanguage2FieldNameTranslator fieldNameTranslator;

    @Nonnull
    private final IndexingAnalyzerFactory indexingAnalyzerFactory;


    @Inject
    public LuceneShortFormMatcher(@Nonnull LuceneSearchStringTokenizer searchStringTokenizer,
                                  @Nonnull DictionaryLanguage2FieldNameTranslator fieldNameTranslator,
                                  @Nonnull IndexingAnalyzerFactory indexingAnalyzerFactory) {
        this.searchStringTokenizer = searchStringTokenizer;
        this.fieldNameTranslator = fieldNameTranslator;
        this.indexingAnalyzerFactory = indexingAnalyzerFactory;
    }

    @Nonnull
    public Stream<ShortFormMatch> getShortFormMatches(@Nonnull EntityShortForms entityShortForms,
                                                      @Nonnull Set<DictionaryLanguage> languages,
                                                      @Nonnull List<SearchString> searchStrings) {
        var tokenizedSearchStrings = searchStringTokenizer.getTokenizedSearchStrings(searchStrings);
        var shortForms = entityShortForms.getShortForms();
        return shortForms.entrySet()
                         .stream()
                         .filter(entry -> languages.contains(entry.getKey()))
                         .flatMap(entry -> getShortFormMatch(entityShortForms.getEntity(),
                                                             entry.getKey(),
                                                             entry.getValue(),
                                                             tokenizedSearchStrings).stream());
    }


    private Optional<ShortFormMatch> getShortFormMatch(@Nonnull OWLEntity entity,
                                                       @Nonnull DictionaryLanguage dictionaryLanguage,
                                                       @Nonnull String shortForm,
                                                       @Nonnull Set<String> searchTokens) {

        var resultBuilder = ImmutableSet.<ShortFormMatchPosition>builder();

        var ngramFieldName = fieldNameTranslator.getEdgeNGramFieldName(dictionaryLanguage);
        addPossibleTokenMatches(shortForm, ngramFieldName, searchTokens, resultBuilder);

        var wordFieldName = fieldNameTranslator.getWordFieldName(dictionaryLanguage);
        addPossibleTokenMatches(shortForm, wordFieldName, searchTokens, resultBuilder);


        var positionsList = ImmutableList.copyOf(resultBuilder.build());
        if (positionsList.isEmpty()) {
            return Optional.empty();
        }
        var shortFormMatch = ShortFormMatch.get(entity, shortForm, dictionaryLanguage,
                                                positionsList);
        return Optional.of(shortFormMatch);
    }

    private void addPossibleTokenMatches(@Nonnull String shortForm,
                                         @Nonnull String luceneFieldName,
                                         @Nonnull Set<String> searchTokens,
                                         @Nonnull ImmutableSet.Builder<ShortFormMatchPosition> resultBuilder) {
        var analyzer = indexingAnalyzerFactory.get();
        try (var tokenStream = analyzer.tokenStream(luceneFieldName, shortForm)) {
            var charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
            var offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                var token = charTermAttribute.toString();
                if (searchTokens.contains(token)) {
                    var startOffset = offsetAttribute.startOffset();
                    var endOffset = startOffset + token.length();
                    var shortFormMatchPosition = ShortFormMatchPosition.get(startOffset, endOffset);
                    resultBuilder.add(shortFormMatchPosition);
                }
            }
            tokenStream.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}