package com.gleaped.italianlearning.content

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Every exercise type from docs/lesson-format.md. Selection is driven by the
 * JSON `type` field through [ExerciseSerializer]; unknown types decode to
 * [Unsupported] instead of failing the whole unit.
 */
@Serializable(with = ExerciseSerializer::class)
sealed interface Exercise {
    val id: String?
    val instruction: String?
    val explanation: String?
    /** Lesson stage label (warmup, learn, practise, use), when the content gives one. */
    val stage: String? get() = null

    /** Whether this exercise produces a right/almost/wrong result. */
    val scored: Boolean get() = true
}

@Serializable
data class ItEn(val it: String, val en: String = "")

@Serializable
@SerialName("grammar_note")
data class GrammarNote(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val title: String = "",
    val body: String = "",
    val examples: List<ItEn> = emptyList(),
) : Exercise {
    override val scored: Boolean get() = false
}

@Serializable
@SerialName("flashcard")
data class Flashcard(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val front: String,
    val back: String,
    val example: String? = null,
) : Exercise

@Serializable
@SerialName("multiple_choice")
data class MultipleChoice(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val prompt: String,
    val options: List<String>,
    val answer: Int,
) : Exercise

@Serializable
@SerialName("gap_fill")
data class GapFill(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val sentence: String,
    val answers: List<String>,
    val hint: String? = null,
) : Exercise

@Serializable
data class ConjugationItem(val person: String, val answer: String)

@Serializable
@SerialName("conjugation")
data class Conjugation(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val verb: String,
    val tense: String = "",
    val items: List<ConjugationItem>,
) : Exercise

@Serializable
@SerialName("sentence_build")
data class SentenceBuild(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val translation: String = "",
    val tiles: List<String>,
    val distractors: List<String> = emptyList(),
) : Exercise

@Serializable
@SerialName("translate")
data class Translate(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val direction: String = DIRECTION_EN_IT,
    val source: String,
    val answers: List<String>,
) : Exercise {
    /** True when the learner reads Italian and writes English. */
    val fromItalian: Boolean get() = direction == DIRECTION_IT_EN

    companion object {
        const val DIRECTION_IT_EN = "it_en"
        const val DIRECTION_EN_IT = "en_it"
    }
}

@Serializable
data class MatchPair(val left: String, val right: String)

@Serializable
@SerialName("match_pairs")
data class MatchPairs(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val pairs: List<MatchPair>,
) : Exercise

@Serializable
@SerialName("error_correction")
data class ErrorCorrection(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val sentence: String,
    val answers: List<String>,
) : Exercise

@Serializable
@SerialName("listen_transcribe")
data class ListenTranscribe(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val audioText: String,
    val answers: List<String>,
    val slow: Boolean = false,
) : Exercise

@Serializable
@SerialName("listen_choose")
data class ListenChoose(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val audioText: String,
    val question: String = "",
    val options: List<String>,
    val answer: Int,
) : Exercise

@Serializable
data class ReadingQuestion(
    val question: String,
    val options: List<String>,
    val answer: Int,
)

@Serializable
@SerialName("reading")
data class Reading(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val title: String = "",
    val passage: String,
    val questions: List<ReadingQuestion> = emptyList(),
) : Exercise

@Serializable
@SerialName("speak_repeat")
data class SpeakRepeat(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val text: String,
    val translation: String? = null,
) : Exercise

@Serializable
@SerialName("speak_respond")
data class SpeakRespond(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val prompt: String,
    val keywords: List<String> = emptyList(),
    val modelAnswer: String = "",
) : Exercise

@Serializable
data class RoleplayTurn(
    val speaker: String,
    val text: String,
    val hint: String? = null,
    val keywords: List<String> = emptyList(),
) {
    val isNpc: Boolean get() = !speaker.equals(SPEAKER_YOU, ignoreCase = true)

    companion object {
        const val SPEAKER_NPC = "npc"
        const val SPEAKER_YOU = "you"
    }
}

@Serializable
@SerialName("roleplay")
data class Roleplay(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val scenario: String = "",
    val turns: List<RoleplayTurn>,
) : Exercise

@Serializable
@SerialName("free_write")
data class FreeWrite(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val prompt: String,
    val minWords: Int = 0,
    val checklist: List<String> = emptyList(),
    val modelAnswer: String = "",
) : Exercise

@Serializable
@SerialName("choose_mood")
data class ChooseMood(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    /** Sentence with `___` where the verb goes. */
    val sentence: String,
    /** Infinitive shown as a hint, e.g. "essere". */
    val verb: String = "",
    val options: List<String> = DEFAULT_OPTIONS,
    /** Index into [options]. */
    val answer: Int,
    /** Accepted verb forms; when empty only the mood is asked. */
    val forms: List<String> = emptyList(),
    /** Words that trigger the mood, shown in feedback. */
    val trigger: String? = null,
) : Exercise {
    companion object {
        val DEFAULT_OPTIONS = listOf("indicativo", "congiuntivo")
    }
}

@Serializable
@SerialName("find_trigger")
data class FindTrigger(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val sentence: String,
    /** Exact words of [sentence] that trigger the mood, e.g. "prima che". */
    val trigger: String,
) : Exercise

@Serializable
@SerialName("transform")
data class Transform(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val source: String,
    /** The change to make, e.g. "Dubito che…" or "→ passato". */
    val cue: String,
    val answers: List<String>,
    val hint: String? = null,
) : Exercise

@Serializable
data class BucketItem(val text: String, val bucket: Int)

@Serializable
@SerialName("sort_buckets")
data class SortBuckets(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val buckets: List<String>,
    val items: List<BucketItem>,
) : Exercise

@Serializable
@SerialName("minimal_pair")
data class MinimalPair(
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
    val sentences: List<String>,
    val question: String,
    val answer: Int,
    val translations: List<String> = emptyList(),
) : Exercise

/** An exercise whose `type` this app version does not know. Shown as skippable. */
@Serializable
data class Unsupported(
    val type: String = "",
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
) : Exercise {
    override val scored: Boolean get() = false
}

/** An exercise that failed to decode (e.g. a required field missing). Shown as skippable. */
data class Invalid(
    val type: String,
    val error: String,
    override val id: String? = null,
    override val instruction: String? = null,
    override val explanation: String? = null,
    override val stage: String? = null,
) : Exercise {
    override val scored: Boolean get() = false
}

object ExerciseTypes {
    const val GRAMMAR_NOTE = "grammar_note"
    const val FLASHCARD = "flashcard"
    const val MULTIPLE_CHOICE = "multiple_choice"
    const val GAP_FILL = "gap_fill"
    const val CONJUGATION = "conjugation"
    const val SENTENCE_BUILD = "sentence_build"
    const val TRANSLATE = "translate"
    const val MATCH_PAIRS = "match_pairs"
    const val ERROR_CORRECTION = "error_correction"
    const val LISTEN_TRANSCRIBE = "listen_transcribe"
    const val LISTEN_CHOOSE = "listen_choose"
    const val READING = "reading"
    const val SPEAK_REPEAT = "speak_repeat"
    const val SPEAK_RESPOND = "speak_respond"
    const val ROLEPLAY = "roleplay"
    const val FREE_WRITE = "free_write"
    const val CHOOSE_MOOD = "choose_mood"
    const val FIND_TRIGGER = "find_trigger"
    const val TRANSFORM = "transform"
    const val SORT_BUCKETS = "sort_buckets"
    const val MINIMAL_PAIR = "minimal_pair"

    val ALL: List<String> = listOf(
        GRAMMAR_NOTE, FLASHCARD, MULTIPLE_CHOICE, GAP_FILL, CONJUGATION, SENTENCE_BUILD,
        TRANSLATE, MATCH_PAIRS, ERROR_CORRECTION, LISTEN_TRANSCRIBE, LISTEN_CHOOSE, READING,
        SPEAK_REPEAT, SPEAK_RESPOND, ROLEPLAY, FREE_WRITE,
        CHOOSE_MOOD, FIND_TRIGGER, TRANSFORM, SORT_BUCKETS, MINIMAL_PAIR,
    )

    /** The JSON `type` name of a decoded exercise. */
    fun of(e: Exercise): String = when (e) {
        is GrammarNote -> GRAMMAR_NOTE
        is Flashcard -> FLASHCARD
        is MultipleChoice -> MULTIPLE_CHOICE
        is GapFill -> GAP_FILL
        is Conjugation -> CONJUGATION
        is SentenceBuild -> SENTENCE_BUILD
        is Translate -> TRANSLATE
        is MatchPairs -> MATCH_PAIRS
        is ErrorCorrection -> ERROR_CORRECTION
        is ListenTranscribe -> LISTEN_TRANSCRIBE
        is ListenChoose -> LISTEN_CHOOSE
        is Reading -> READING
        is SpeakRepeat -> SPEAK_REPEAT
        is SpeakRespond -> SPEAK_RESPOND
        is Roleplay -> ROLEPLAY
        is FreeWrite -> FREE_WRITE
        is ChooseMood -> CHOOSE_MOOD
        is FindTrigger -> FIND_TRIGGER
        is Transform -> TRANSFORM
        is SortBuckets -> SORT_BUCKETS
        is MinimalPair -> MINIMAL_PAIR
        is Unsupported -> e.type
        is Invalid -> e.type
    }
}

/** Picks the concrete serializer from the `type` field. */
object ExerciseSerializer : JsonContentPolymorphicSerializer<Exercise>(Exercise::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Exercise> {
        val type = (element.jsonObject["type"] as? JsonPrimitive)?.content.orEmpty()
        return when (type) {
            ExerciseTypes.GRAMMAR_NOTE -> GrammarNote.serializer()
            ExerciseTypes.FLASHCARD -> Flashcard.serializer()
            ExerciseTypes.MULTIPLE_CHOICE -> MultipleChoice.serializer()
            ExerciseTypes.GAP_FILL -> GapFill.serializer()
            ExerciseTypes.CONJUGATION -> Conjugation.serializer()
            ExerciseTypes.SENTENCE_BUILD -> SentenceBuild.serializer()
            ExerciseTypes.TRANSLATE -> Translate.serializer()
            ExerciseTypes.MATCH_PAIRS -> MatchPairs.serializer()
            ExerciseTypes.ERROR_CORRECTION -> ErrorCorrection.serializer()
            ExerciseTypes.LISTEN_TRANSCRIBE -> ListenTranscribe.serializer()
            ExerciseTypes.LISTEN_CHOOSE -> ListenChoose.serializer()
            ExerciseTypes.READING -> Reading.serializer()
            ExerciseTypes.SPEAK_REPEAT -> SpeakRepeat.serializer()
            ExerciseTypes.SPEAK_RESPOND -> SpeakRespond.serializer()
            ExerciseTypes.ROLEPLAY -> Roleplay.serializer()
            ExerciseTypes.FREE_WRITE -> FreeWrite.serializer()
            ExerciseTypes.CHOOSE_MOOD -> ChooseMood.serializer()
            ExerciseTypes.FIND_TRIGGER -> FindTrigger.serializer()
            ExerciseTypes.TRANSFORM -> Transform.serializer()
            ExerciseTypes.SORT_BUCKETS -> SortBuckets.serializer()
            ExerciseTypes.MINIMAL_PAIR -> MinimalPair.serializer()
            else -> Unsupported.serializer()
        }
    }
}
