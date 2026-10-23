<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import { t } from '@nextcloud/l10n'
import {
	NcButton,
	NcColorPicker,
	NcModal,
	NcTextField,
} from '@nextcloud/vue'
import { computed, ref } from 'vue'
import CheckIcon from 'vue-material-design-icons/Check.vue'
import NumberInput from '@/components/AppointmentConfigModal/NumberInput.vue'
import TimePicker from '@/components/Shared/TimePicker.vue'
import NocturnLesson from '@/models/nocturnLesson.js'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const props = defineProps<{
	lesson: NocturnLesson
	isNew: boolean
}>()

const emit = defineEmits<{
	close: []
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const isLoading = ref(false)
const editing = ref<NocturnLesson>(props.lesson.clone())

const formTitle = props.isNew ? t('nocturnkalender', 'Create lesson') : t('nocturnkalender', 'Edit lesson')
const saveButtonText = props.isNew ? t('nocturnkalender', 'Create') : t('nocturnkalender', 'Save')

const weekdayOptions = computed(() => [
	t('nocturnkalender', 'Monday'),
	t('nocturnkalender', 'Tuesday'),
	t('nocturnkalender', 'Wednesday'),
	t('nocturnkalender', 'Thursday'),
	t('nocturnkalender', 'Friday'),
	t('nocturnkalender', 'Saturday'),
	t('nocturnkalender', 'Sunday'),
])

const startAsDate = computed<Date>(() => {
	const date = new Date()
	date.setHours(editing.value.startHour, editing.value.startMinute, 0, 0)
	return date
})

function onStartTimeChanged(date: Date): void {
	editing.value.startHour = date.getHours()
	editing.value.startMinute = date.getMinutes()
}

async function save(): Promise<void> {
	isLoading.value = true
	try {
		await nocturnScheduleStore.saveLesson(editing.value)
		emit('close')
	} catch (error) {
		logger.error('Failed to save NocTurn lesson', { error })
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
		<div class="nocturn-lesson-modal">
			<h2>{{ formTitle }}</h2>
			<div class="nocturn-lesson-modal__row nocturn-lesson-modal__row--name-color">
				<NcColorPicker
					v-model="editing.color"
					:advancedFields="true">
					<div
						class="nocturn-lesson-modal__color-dot"
						:style="{ 'background-color': editing.color }" />
				</NcColorPicker>
				<NcTextField
					v-model="editing.name"
					class="nocturn-lesson-modal__name"
					:label="t('nocturnkalender', 'Name')"
					:placeholder="t('nocturnkalender', 'e.g. Maths')" />
			</div>

			<div class="nocturn-lesson-modal__row">
				<label>{{ t('nocturnkalender', 'Weekday') }}</label>
				<select v-model.number="editing.weekday" class="nocturn-lesson-modal__weekday-select">
					<option v-for="(label, index) in weekdayOptions" :key="index" :value="index">
						{{ label }}
					</option>
				</select>
			</div>

			<div class="nocturn-lesson-modal__row nocturn-lesson-modal__row--wrapped">
				<div>
					<label>{{ t('nocturnkalender', 'Start time') }}</label>
					<TimePicker :date="startAsDate" @change="onStartTimeChanged" />
				</div>
				<NumberInput
					v-model="editing.durationMinutes"
					:label="t('nocturnkalender', 'Duration (minutes)')" />
			</div>

			<p class="nocturn-lesson-modal__hint">
				{{ t('nocturnkalender', 'Ends at {time}', { time: editing.endTimeLabel }) }}
			</p>

			<NcButton
				class="nocturn-lesson-modal__submit"
				variant="primary"
				:disabled="!editing.name || isLoading"
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
.nocturn-lesson-modal {
	padding: 12px 20px 20px;

	&__row {
		margin-bottom: 12px;

		&--name-color {
			display: flex;
			align-items: center;
			gap: 8px;
		}

		&--wrapped {
			display: flex;
			gap: 16px;

			> * {
				flex: 1;
			}
		}
	}

	&__name {
		flex: 1;
	}

	&__color-dot {
		width: 32px;
		height: 32px;
		border-radius: 50%;
		cursor: pointer;
	}

	&__weekday-select {
		width: 100%;
	}

	&__hint {
		color: var(--color-text-maxcontrast);
		margin-top: -4px;
		margin-bottom: 12px;
	}

	&__submit {
		margin-top: 8px;
	}
}
</style>
