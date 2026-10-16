<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import type NocturnRotation from '@/models/nocturnRotation.js'

import { showError, showSuccess } from '@nextcloud/dialogs'
import { t } from '@nextcloud/l10n'
import {
	NcButton,
	NcDateTimePickerNative,
	NcModal,
} from '@nextcloud/vue'
import { ref } from 'vue'
import CheckIcon from 'vue-material-design-icons/Check.vue'
import NumberInput from '@/components/AppointmentConfigModal/NumberInput.vue'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const props = defineProps<{
	rotation: NocturnRotation
}>()

const emit = defineEmits<{
	close: []
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const isLoading = ref(false)
const startDate = ref<Date>(new Date())
const months = ref<number>(3)

/**
 * Days since 1970-01-01 UTC for the calendar date picked by the user,
 * matching Java's LocalDate.toEpochDay() (used by the NocTurn Android app).
 *
 * @param {Date} date Date to convert
 * @return {number}
 */
function toEpochDay(date: Date): number {
	const utcMidnight = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate())
	return Math.floor(utcMidnight / 86400000)
}

async function apply(): Promise<void> {
	isLoading.value = true
	try {
		const result = await nocturnScheduleStore.applyRotation(props.rotation.uid, toEpochDay(startDate.value), months.value)
		showSuccess(t('nocturnkalender', '{count} days generated', { count: result.generatedDays }))
		emit('close')
	} catch (error) {
		logger.error('Failed to apply NocTurn rotation', { error })
		showError(t('nocturnkalender', 'Could not apply rotation'))
	} finally {
		isLoading.value = false
	}
}
</script>

<template>
	<NcModal
		size="small"
		:name="t('nocturnkalender', 'Apply rotation')"
		@close="$emit('close')">
		<div class="nocturn-apply-rotation-modal">
			<h2>{{ t('nocturnkalender', 'Apply “{name}”', { name: rotation.name }) }}</h2>
			<p class="nocturn-apply-rotation-modal__hint">
				{{ t('nocturnkalender', 'Generates calendar entries for this rotation’s pattern, repeated over the chosen time range. Re-applying overwrites previously generated entries in the same range.') }}
			</p>

			<div class="nocturn-apply-rotation-modal__row">
				<label>{{ t('nocturnkalender', 'Start date') }}</label>
				<NcDateTimePickerNative
					v-model="startDate"
					:hideLabel="true"
					type="date" />
			</div>

			<NumberInput
				v-model="months"
				class="nocturn-apply-rotation-modal__row"
				:label="t('nocturnkalender', 'Months to generate')" />

			<NcButton
				class="nocturn-apply-rotation-modal__submit"
				variant="primary"
				:disabled="isLoading || months < 1"
				@click="apply">
				<template #icon>
					<CheckIcon :size="20" />
				</template>
				{{ t('nocturnkalender', 'Apply') }}
			</NcButton>
		</div>
	</NcModal>
</template>

<style lang="scss" scoped>
.nocturn-apply-rotation-modal {
	padding: 12px 20px 20px;

	&__hint {
		color: var(--color-text-maxcontrast);
		margin-bottom: 12px;
	}

	&__row {
		margin-bottom: 12px;
	}

	&__submit {
		margin-top: 8px;
	}
}
</style>
