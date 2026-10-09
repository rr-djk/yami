package app.yamy.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yamy.engine.ChatEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SkillsMode { LIST, DICTATE, EDIT }

data class SkillEditor(
    val originalName: String? = null,
    val name: String = "",
    val description: String = "",
    val instructions: String = "",
)

data class SkillsUiState(
    val mode: SkillsMode = SkillsMode.LIST,
    val skills: List<Skill> = emptyList(),
    val generating: Boolean = false,
    val editor: SkillEditor = SkillEditor(),
    val message: String? = null,
)

class SkillsViewModel(
    private val repository: SkillRepository,
    private val creator: SkillCreator,
    private val engine: ChatEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(SkillsUiState())
    val state: StateFlow<SkillsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { repository.list() }
            _state.update { it.copy(skills = all) }
        }
    }

    fun startDictation() = _state.update { it.copy(mode = SkillsMode.DICTATE, message = null) }

    fun newManual() = _state.update { it.copy(mode = SkillsMode.EDIT, editor = SkillEditor(), message = null) }

    fun edit(skill: Skill) = _state.update {
        it.copy(mode = SkillsMode.EDIT, editor = SkillEditor(skill.name, skill.name, skill.description, skill.instructions), message = null)
    }

    fun updateEditor(transform: (SkillEditor) -> SkillEditor) = _state.update { it.copy(editor = transform(it.editor)) }

    fun backToList() = _state.update { it.copy(mode = SkillsMode.LIST, message = null) }

    /** Yamy rédige le skill à partir de la description de l'utilisateur ; l'édition reste obligatoire avant d'enregistrer. */
    fun generate(request: String) {
        if (request.isBlank() || _state.value.generating) return
        _state.update { it.copy(generating = true, message = null) }
        viewModelScope.launch {
            try {
                val draft = creator.draft(request)
                val skill = draft.skill
                val editor = if (skill != null) {
                    SkillEditor(null, skill.name, skill.description, skill.instructions)
                } else {
                    SkillEditor(null, SkillFormat.slugify(request.take(30)), request.take(SkillFormat.MAX_DESCRIPTION), draft.raw)
                }
                _state.update {
                    it.copy(
                        mode = SkillsMode.EDIT,
                        editor = editor,
                        generating = false,
                        message = draft.problem?.let { p -> "Brouillon à corriger : $p" } ?: "Relis le skill avant de l'enregistrer.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(generating = false, message = "Génération impossible : ${e.message}") }
            }
        }
    }

    fun save() {
        val e = _state.value.editor
        val skill = Skill(e.name.trim(), e.description.trim(), e.instructions.trim())
        val invalid = SkillFormat.validate(skill) as? SkillFormat.Result.Invalid
        if (invalid != null) {
            _state.update { it.copy(message = invalid.reason) }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (e.originalName != null && e.originalName != skill.name) repository.delete(e.originalName)
                repository.save(skill)
            }
            engine.invalidateConversation()
            _state.update { it.copy(mode = SkillsMode.LIST, message = null) }
            refresh()
        }
    }

    fun delete(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(name) }
            engine.invalidateConversation()
            refresh()
        }
    }
}
