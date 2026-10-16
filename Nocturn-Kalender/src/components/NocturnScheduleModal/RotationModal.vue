<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import type NocturnShift from '@/models/nocturnShift.js'

import { t } from '@nextcloud/l10n'
import {
	NcButton,
	NcModal,
	NcSelect,
	NcTextField,
} from '@nextcloud/vue'
import { computed, ref } from 'vue'
import CheckIcon from 'vue-material-design-icons/Check.vue'
import MinusIcon from 'vue-material-design-icons/Minus.vue'
import PlusIcon from 'vue-material-design-icons/Plus.vue'
import NocturnRotation from '@/models/nocturnRotation.js'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

interface ShiftOption {
	value: string | null
	label: string
	color?: string
}

const props = defineProps<{
	rotation: NocturnRotation
	isNew: boolean
	shifts: NocturnShift[]
}>()

const emit = defineEmits<{
	close: []
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const isLoading = ref(false)
const editing = ref<NocturnRotation>(props.rotation.clone())
if (editing.value.days.length === 0) {
	editing.value.days = [null, null, null, null, null, null, null]
}

const formTitle = props.isNew ? t('nocturnkalender', 'Create rotation') : t('nocturnkalender', 'Edit rotation')
const saveButtonText = props.isNew ? t('nocturnkalender', 'Create') : t('nocturnkalender', 'Save')

const freeDayOption: ShiftOption = { value: null, label: t('nocturnkalender', 'Free day') }

const shiftOptions = computed<ShiftOption[]>(() => {
	return [
		freeDayOption,
		...props.shifts.map((shift) => ({ value: shift.uid, label: `${shift.name} (${shift.startTimeLabel})`, color: shift.color })),
	]
})

function optionForDay(shiftUid: string | null): ShiftOption {
	return shiftOptions.value.find((option) => option.value === shiftUid) ?? freeDayOption
}

function setDay(index: number, option: ShiftOption | null): void {
	editing.value.days[index] = option?.value ?? null
}

function addDay(): void {
	editing.value.days.push(null)
}

function removeDay(index: number): void {
	editing.value.days.splice(index, 1)
}

const canSave = computed<boolean>(() => !!editing.value.name && editing.value.days.length > 0 && !isLoading.value)

async function save(): Promise<void> {
	isLoading.value = true
	try {
		await nocturnScheduleStore.saveRotation(editing.value)
		emit('close')
	} catch (error) {
		logger.error('Failed to save NocTurn rotation', { error })
	} finally {
		isLoading.value = false
	}
}
</script>

<template>
	<NcModal
		size="normal"
		:name="formTitle"
		@close="$emit('close')">
		<div class="nocturn-rotation-modal">
			<h2>{{ formTitle }}</h2>
			<NcTextField
				v-model="editing.name"
				class="nocturn-rotation-modal__row"
				:label="t('nocturnkalender', 'Name')"
				:placeholder="t('nocturnkalender', 'e.g. 4-shift rotation')" />

			<p class="nocturn-rotation-modal__hint">
				{{ t('nocturnkalender', 'Define the repeating day-for-day pattern. It has no fixed start date - you choose one later when applying it to the calendar.') }}
			</p>

			<div class="nocturn-rotation-modal__days">
				<div
					v-for="(shiftUid, index) in editing.days"
					:key="index"
					class="nocturn-rotation-modal__day">
					<span class="nocturn-rotation-modal__day-label">{{ t('nocturnkalender', 'Day {n}', { n: index + 1 }) }}</span>
					<NcSelect
						class="nocturn-rotation-modal__day-select"
						:modelValue="optionForDay(shiftUid)"
						:options="shiftOptions"
						:clearable="false"
						label="label"
						@update:modelValue="setDay(index, $event)" />
					<NcButton
						type="tertiary"
						:aria-label="t('nocturnkalender', 'Remove day')"
						:disabled="editing.days.length <= 1"
						@click="removeDay(index)">
						<template #icon>
							<MinusIcon :size="18" />
						</template>
					</NcButton>
				</div>
			</div>

			<NcButton type="tertiary" @click="addDay">
				<template #icon>
					<PlusIcon :size="18" />
				</template>
				{{ t('nocturnkalender', 'Add day') }}
			</NcButton>

			<NcButton
				class="nocturn-rotation-modal__submit"
				variant="primary"
				:disabled="!canSave"
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
.nocturn-rotation-modal {
	padding: 12px 20px 20px;

	&__row {
		margin-bottom: 12px;
	}

	&__hint {
		color: var(--color-text-maxcontrast);
		margin-bottom: 12px;
	}

	&__days {
		display: flex;
		flex-direction: column;
		gap: 8px;
		margin-bottom: 12px;
		max-height: 320px;
		overflow-y: auto;
	}

	&__day {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	&__day-label {
		width: 64px;
		flex-shrink: 0;
		color: var(--color-text-maxcontrast);
	}

	&__day-select {
		flex: 1;
	}

	&__submit {
		margin-top: 12px;
	}
}
</style>
