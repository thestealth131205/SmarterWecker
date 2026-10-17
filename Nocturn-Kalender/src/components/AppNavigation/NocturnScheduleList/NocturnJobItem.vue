<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import type NocturnJob from '@/models/nocturnJob.js'

import { showError } from '@nextcloud/dialogs'
import { t } from '@nextcloud/l10n'
import {
	NcActionButton as ActionButton,
	NcAppNavigationItem as AppNavigationItem,
} from '@nextcloud/vue'
import { computed, ref } from 'vue'
import AccountPlusOutlineIcon from 'vue-material-design-icons/AccountPlusOutline.vue'
import CalendarSyncIcon from 'vue-material-design-icons/CalendarSync.vue'
import CheckboxBlank from 'vue-material-design-icons/CheckboxBlankOutline.vue'
import CheckboxMarked from 'vue-material-design-icons/CheckboxMarked.vue'
import ChevronDownIcon from 'vue-material-design-icons/ChevronDown.vue'
import ChevronUpIcon from 'vue-material-design-icons/ChevronUp.vue'
import ClockPlusIcon from 'vue-material-design-icons/ClockPlusOutline.vue'
import PencilIcon from 'vue-material-design-icons/PencilOutline.vue'
import PlusIcon from 'vue-material-design-icons/Plus.vue'
import DeleteIcon from 'vue-material-design-icons/TrashCanOutline.vue'
import ApplyRotationModal from '@/components/NocturnScheduleModal/ApplyRotationModal.vue'
import JobModal from '@/components/NocturnScheduleModal/JobModal.vue'
import RotationModal from '@/components/NocturnScheduleModal/RotationModal.vue'
import ShareJobModal from '@/components/NocturnScheduleModal/ShareJobModal.vue'
import ShiftModal from '@/components/NocturnScheduleModal/ShiftModal.vue'
import NocturnRotation from '@/models/nocturnRotation.js'
import NocturnShift from '@/models/nocturnShift.js'
import useNocturnScheduleStore, { nocturnJobVisibilityKey } from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const props = defineProps<{
	job: NocturnJob
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const expanded = ref(false)

const shifts = computed(() => nocturnScheduleStore.shiftsForJob(props.job.uid))
const rotations = computed(() => nocturnScheduleStore.rotationsForJob(props.job.uid))

const visibilityKey = computed(() => nocturnJobVisibilityKey(null, props.job.uid))
const isVisible = computed(() => nocturnScheduleStore.isJobVisible(visibilityKey.value))
// Representative color for the checkbox: the earliest shift's, falling
// back to a neutral gray before the job has any shifts yet.
const jobColor = computed(() => shifts.value[0]?.color ?? '#999999')

function toggleVisibility(): void {
	nocturnScheduleStore.toggleJobVisibility(visibilityKey.value)
}

const showJobEditor = ref(false)
const shiftModalTarget = ref<NocturnShift | null>(null)
const shiftModalIsNew = ref(false)
const rotationModalTarget = ref<NocturnRotation | null>(null)
const rotationModalIsNew = ref(false)
const applyRotationTarget = ref<NocturnRotation | null>(null)
const showShareModal = ref(false)

function toggleExpanded(): void {
	expanded.value = !expanded.value
}

function openNewShift(): void {
	shiftModalTarget.value = NocturnShift.createDefault(props.job.uid)
	shiftModalIsNew.value = true
	expanded.value = true
}

function openEditShift(shift: NocturnShift): void {
	shiftModalTarget.value = shift
	shiftModalIsNew.value = false
}

async function removeShift(shift: NocturnShift): Promise<void> {
	try {
		await nocturnScheduleStore.deleteShift(shift.uid)
	} catch (error) {
		logger.error('Failed to delete NocTurn shift', { error })
		showError(t('nocturnkalender', 'Could not delete shift'))
	}
}

function openNewRotation(): void {
	rotationModalTarget.value = NocturnRotation.createDefault(props.job.uid)
	rotationModalIsNew.value = true
	expanded.value = true
}

function openEditRotation(rotation: NocturnRotation): void {
	rotationModalTarget.value = rotation
	rotationModalIsNew.value = false
}

async function removeRotation(rotation: NocturnRotation): Promise<void> {
	try {
		await nocturnScheduleStore.deleteRotation(rotation.uid)
	} catch (error) {
		logger.error('Failed to delete NocTurn rotation', { error })
		showError(t('nocturnkalender', 'Could not delete rotation'))
	}
}

async function removeJob(): Promise<void> {
	try {
		await nocturnScheduleStore.deleteJob(props.job.uid)
	} catch (error) {
		logger.error('Failed to delete NocTurn job', { error })
		showError(t('nocturnkalender', 'Could not delete job'))
	}
}
</script>

<template>
	<div class="nocturn-job-item">
		<AppNavigationItem
			:name="job.name"
			@click="toggleExpanded">
			<template #icon>
				<CheckboxMarked
					v-if="isVisible"
					:size="20"
					:fillColor="jobColor"
					@click.prevent.stop="toggleVisibility" />
				<CheckboxBlank
					v-else
					:size="20"
					:fillColor="jobColor"
					@click.prevent.stop="toggleVisibility" />
			</template>
			<template #counter>
				<ChevronUpIcon v-if="expanded" :size="16" />
				<ChevronDownIcon v-else :size="16" />
			</template>
			<template #actions>
				<ActionButton :closeAfterClick="true" @click="openNewShift">
					<template #icon>
						<ClockPlusIcon :size="20" />
					</template>
					{{ t('nocturnkalender', 'Add shift') }}
				</ActionButton>
				<ActionButton :closeAfterClick="true" @click="openNewRotation">
					<template #icon>
						<PlusIcon :size="20" />
					</template>
					{{ t('nocturnkalender', 'Add rotation') }}
				</ActionButton>
				<ActionButton :closeAfterClick="true" @click="showJobEditor = true">
					<template #icon>
						<PencilIcon :size="20" />
					</template>
					{{ t('nocturnkalender', 'Edit') }}
				</ActionButton>
				<ActionButton :closeAfterClick="true" @click="showShareModal = true">
					<template #icon>
						<AccountPlusOutlineIcon :size="20" />
					</template>
					{{ t('nocturnkalender', 'Share') }}
				</ActionButton>
				<ActionButton :closeAfterClick="true" @click="removeJob">
					<template #icon>
						<DeleteIcon :size="20" />
					</template>
					{{ t('nocturnkalender', 'Delete') }}
				</ActionButton>
			</template>
		</AppNavigationItem>

		<div v-if="expanded" class="nocturn-job-item__children">
			<div v-if="shifts.length === 0 && rotations.length === 0" class="nocturn-job-item__empty">
				{{ t('nocturnkalender', 'No shifts yet') }}
			</div>

			<div
				v-for="shift in shifts"
				:key="shift.uid"
				class="nocturn-job-item__child">
				<span class="nocturn-job-item__color-dot" :style="{ 'background-color': shift.color }" />
				<span class="nocturn-job-item__child-name">{{ shift.name }}</span>
				<span class="nocturn-job-item__child-time">{{ shift.startTimeLabel }}–{{ shift.endTimeLabel }}</span>
				<button class="nocturn-job-item__child-action" :aria-label="t('nocturnkalender', 'Edit')" @click="openEditShift(shift)">
					<PencilIcon :size="16" />
				</button>
				<button class="nocturn-job-item__child-action" :aria-label="t('nocturnkalender', 'Delete')" @click="removeShift(shift)">
					<DeleteIcon :size="16" />
				</button>
			</div>

			<div
				v-for="rotation in rotations"
				:key="rotation.uid"
				class="nocturn-job-item__child">
				<CalendarSyncIcon :size="18" decorative />
				<span class="nocturn-job-item__child-name">{{ rotation.name }}</span>
				<button class="nocturn-job-item__child-action" :aria-label="t('nocturnkalender', 'Apply to calendar')" @click="applyRotationTarget = rotation">
					<CalendarSyncIcon :size="16" />
				</button>
				<button class="nocturn-job-item__child-action" :aria-label="t('nocturnkalender', 'Edit')" @click="openEditRotation(rotation)">
					<PencilIcon :size="16" />
				</button>
				<button class="nocturn-job-item__child-action" :aria-label="t('nocturnkalender', 'Delete')" @click="removeRotation(rotation)">
					<DeleteIcon :size="16" />
				</button>
			</div>
		</div>

		<JobModal v-if="showJobEditor" :job="job" :isNew="false" @close="showJobEditor = false" />
		<ShiftModal v-if="shiftModalTarget" :shift="shiftModalTarget" :isNew="shiftModalIsNew" @close="shiftModalTarget = null" />
		<RotationModal v-if="rotationModalTarget" :rotation="rotationModalTarget" :isNew="rotationModalIsNew" :shifts="shifts" @close="rotationModalTarget = null" />
		<ApplyRotationModal v-if="applyRotationTarget" :rotation="applyRotationTarget" @close="applyRotationTarget = null" />
		<ShareJobModal v-if="showShareModal" :job="job" @close="showShareModal = false" />
	</div>
</template>

<style lang="scss" scoped>
.nocturn-job-item {
	&__children {
		padding: 0 8px 8px 44px;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	&__empty {
		color: var(--color-text-maxcontrast);
		font-size: 0.9em;
		padding: 4px 0;
	}

	&__child {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 4px 0;
		font-size: 0.95em;

		&-name {
			flex: 1;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		&-time {
			color: var(--color-text-maxcontrast);
			font-size: 0.85em;
			flex-shrink: 0;
		}

		&-action {
			background: none;
			border: none;
			cursor: pointer;
			display: flex;
			align-items: center;
			color: var(--color-text-maxcontrast);
			padding: 2px;
			border-radius: var(--border-radius);
			flex-shrink: 0;

			&:hover {
				color: var(--color-main-text);
				background-color: var(--color-background-hover);
			}
		}
	}

	&__color-dot {
		width: 12px;
		height: 12px;
		border-radius: 50%;
		flex-shrink: 0;
	}
}
</style>
