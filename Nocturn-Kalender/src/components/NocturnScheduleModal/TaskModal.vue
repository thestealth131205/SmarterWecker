<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import { t } from '@nextcloud/l10n'
import {
	NcButton,
	NcCheckboxRadioSwitch,
	NcDateTimePickerNative,
	NcModal,
	NcTextArea,
	NcTextField,
} from '@nextcloud/vue'
import { computed, ref } from 'vue'
import CheckIcon from 'vue-material-design-icons/Check.vue'
import NocturnTask from '@/models/nocturnTask.js'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const props = defineProps<{
	task: NocturnTask
	isNew: boolean
}>()

const emit = defineEmits<{
	close: []
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const isLoading = ref(false)
const editing = ref<NocturnTask>(props.task.clone())

const formTitle = props.isNew ? t('nocturnkalender', 'Create task') : t('nocturnkalender', 'Edit task')
const saveButtonText = props.isNew ? t('nocturnkalender', 'Create') : t('nocturnkalender', 'Save')

/**
 * Days since 1970-01-01 UTC for the calendar date picked by the user,
 * matching Java's LocalDate.toEpochDay() (used by the NocTurn Android app).
 */
function toEpochDay(date: Date): number {
	const utcMidnight = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate())
	return Math.floor(utcMidnight / 86400000)
}

const dueDate = computed<Date>({
	get: () => new Date(editing.value.dueEpochDay * 86400000),
	set: (date: Date) => {
		editing.value.dueEpochDay = toEpochDay(date)
	},
})

const dueTime = computed<Date>({
	get: () => editing.value.dueEpochMillis ? new Date(editing.value.dueEpochMillis) : new Date(new Date().setHours(9, 0, 0, 0)),
	set: (date: Date) => {
		editing.value.dueEpochMillis = date.getTime()
	},
})

async function save(): Promise<void> {
	if (editing.value.allDay) {
		editing.value.dueEpochMillis = null
	} else if (!editing.value.dueEpochMillis) {
		editing.value.dueEpochMillis = dueTime.value.getTime()
	}
	isLoading.value = true
	try {
		await nocturnScheduleStore.saveTask(editing.value)
		emit('close')
	} catch (error) {
		logger.error('Failed to save NocTurn task', { error })
	} finally {
		isLoading.value = false
	}
}
</script>

<template>
	<NcModal
		size="small"
		:name="formTitle"
		@close="$emit('close')">
		<div class="nocturn-task-modal">
			<h2>{{ formTitle }}</h2>

			<NcTextField
				v-model="editing.title"
				class="nocturn-task-modal__row"
				:label="t('nocturnkalender', 'Title')" />

			<NcTextArea
				v-model="editing.notes"
				class="nocturn-task-modal__row"
				:label="t('nocturnkalender', 'Details')" />

			<div class="nocturn-task-modal__row">
				<label>{{ t('nocturnkalender', 'Due date') }}</label>
				<NcDateTimePickerNative
					v-model="dueDate"
					:hideLabel="true"
					type="date" />
			</div>

			<NcCheckboxRadioSwitch
				v-model="editing.allDay"
				class="nocturn-task-modal__row"
				type="switch">
				{{ t('nocturnkalender', 'All day') }}
			</NcCheckboxRadioSwitch>

			<div v-if="!editing.allDay" class="nocturn-task-modal__row">
				<label>{{ t('nocturnkalender', 'Due time') }}</label>
				<NcDateTimePickerNative
					v-model="dueTime"
					:hideLabel="true"
					type="time" />
			</div>

			<NcButton
				class="nocturn-task-modal__submit"
				variant="primary"
				:disabled="!editing.title || isLoading"
				@click="save">
				<template #icon>
					<CheckIcon :size="20" />
				</template>
				{{ saveButtonText }}
			</NcButton>
		</div>
	</NcModal>
</template>

<style lang="scss" scoped>
.nocturn-task-modal {
	padding: 12px 20px 20px;

	&__row {
		margin-bottom: 12px;
	}

	&__submit {
		margin-top: 8px;
	}
}
</style>
