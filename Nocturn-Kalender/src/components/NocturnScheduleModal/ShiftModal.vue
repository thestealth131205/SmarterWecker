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
import NocturnShift from '@/models/nocturnShift.js'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const props = defineProps<{
	shift: NocturnShift
	isNew: boolean
}>()

const emit = defineEmits<{
	close: []
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const isLoading = ref(false)
const editing = ref<NocturnShift>(props.shift.clone())

const formTitle = props.isNew ? t('nocturnkalender', 'Create shift') : t('nocturnkalender', 'Edit shift')
const saveButtonText = props.isNew ? t('nocturnkalender', 'Create') : t('nocturnkalender', 'Save')

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
		await nocturnScheduleStore.saveShift(editing.value)
		emit('close')
	} catch (error) {
		logger.error('Failed to save NocTurn shift', { error })
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
		<div class="nocturn-shift-modal">
			<h2>{{ formTitle }}</h2>
			<div class="nocturn-shift-modal__row nocturn-shift-modal__row--name-color">
				<NcColorPicker
					v-model="editing.color"
					:advancedFields="true">
					<div
						class="nocturn-shift-modal__color-dot"
						:style="{ 'background-color': editing.color }" />
				</NcColorPicker>
				<NcTextField
					v-model="editing.name"
					class="nocturn-shift-modal__name"
					:label="t('nocturnkalender', 'Name')"
					:placeholder="t('nocturnkalender', 'e.g. Early shift')" />
			</div>

			<div class="nocturn-shift-modal__row nocturn-shift-modal__row--wrapped">
				<div>
					<label>{{ t('nocturnkalender', 'Start time') }}</label>
					<TimePicker :date="startAsDate" @change="onStartTimeChanged" />
				</div>
				<NumberInput
					v-model="editing.durationMinutes"
					:label="t('nocturnkalender', 'Duration (minutes)')" />
			</div>

			<p class="nocturn-shift-modal__hint">
				{{ t('nocturnkalender', 'Ends at {time}', { time: editing.endTimeLabel }) }}
			</p>

			<div class="nocturn-shift-modal__row nocturn-shift-modal__row--wrapped">
				<NumberInput
					v-model="editing.earliestWakeOffsetMinutes"
					:label="t('nocturnkalender', 'Earliest wake-up (minutes before start)')" />
				<NumberInput
					v-model="editing.latestWakeupOffsetMinutes"
					:label="t('nocturnkalender', 'Latest wake-up (minutes before start)')" />
			</div>

			<NcButton
				class="nocturn-shift-modal__submit"
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
.nocturn-shift-modal {
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
