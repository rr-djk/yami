package app.yamy.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroOutlinedButton
import app.yamy.ui.components.RetroRule
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.components.RetroTextField
import app.yamy.ui.theme.RetroColors

@Composable
fun SkillsScreen(viewModel: SkillsViewModel, onClose: () -> Unit) {
    val ui by viewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.skills_title), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary)
            RetroTextButton(
                onClick = if (ui.mode == SkillsMode.LIST) onClose else viewModel::backToList,
                label = stringResource(if (ui.mode == SkillsMode.LIST) R.string.close else R.string.back),
            )
        }
        RetroRule()
        if (ui.generating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = RetroColors.Secondary, trackColor = RetroColors.Surface)
            Text(stringResource(R.string.skill_generating))
        }
        ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        when (ui.mode) {
            SkillsMode.LIST -> SkillList(ui, viewModel)
            SkillsMode.DICTATE -> Dictate(ui.generating, viewModel::generate)
            SkillsMode.EDIT -> Editor(ui.editor, viewModel)
        }
    }
}

@Composable
private fun SkillList(ui: SkillsUiState, viewModel: SkillsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RetroButton(onClick = viewModel::startDictation) { Text(stringResource(R.string.skill_dictate)) }
        RetroOutlinedButton(onClick = viewModel::newManual) { Text(stringResource(R.string.skill_manual)) }
    }
    if (ui.skills.isEmpty()) {
        Text(stringResource(R.string.skills_empty), style = MaterialTheme.typography.bodyLarge)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ui.skills, key = { it.name }) { skill ->
            RetroCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.edit(skill) }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(skill.name, style = MaterialTheme.typography.titleMedium)
                    Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                    RetroTextButton(onClick = { viewModel.delete(skill.name) }, label = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun Dictate(generating: Boolean, onGenerate: (String) -> Unit) {
    var request by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.skill_dictate_hint))
    RetroTextField(value = request, onValueChange = { request = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !generating)
    RetroButton(onClick = { onGenerate(request) }, enabled = request.isNotBlank() && !generating) { Text(stringResource(R.string.skill_generate)) }
}

@Composable
private fun Editor(editor: SkillEditor, viewModel: SkillsViewModel) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RetroTextField(editor.name, { v -> viewModel.updateEditor { it.copy(name = v) } }, label = { Text(stringResource(R.string.skill_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        RetroTextField(editor.description, { v -> viewModel.updateEditor { it.copy(description = v.replace('\n', ' ')) } }, label = { Text(stringResource(R.string.skill_description)) }, modifier = Modifier.fillMaxWidth())
        RetroTextField(editor.instructions, { v -> viewModel.updateEditor { it.copy(instructions = v) } }, label = { Text(stringResource(R.string.skill_instructions)) }, minLines = 6, modifier = Modifier.fillMaxWidth())
        RetroButton(onClick = viewModel::save) { Text(stringResource(R.string.save)) }
    }
}
