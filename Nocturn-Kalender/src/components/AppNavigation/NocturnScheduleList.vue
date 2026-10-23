<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import { showError } from '@nextcloud/dialogs'
import { t } from '@nextcloud/l10n'
import { NcActionButton as ActionButton, NcAppNavigationCaption as AppNavigationCaption, NcAvatar } from '@nextcloud/vue'
import { computed, onMounted, ref } from 'vue'
import CheckboxBlank from 'vue-material-design-icons/CheckboxBlankOutline.vue'
import CheckboxMarked from 'vue-material-design-icons/CheckboxMarked.vue'
import PlusIcon from 'vue-material-design-icons/Plus.vue'
import DeleteIcon from 'vue-material-design-icons/TrashCanOutline.vue'
import NocturnJobItem from '@/components/AppNavigation/NocturnScheduleList/NocturnJobItem.vue'
import JobModal from '@/components/NocturnScheduleModal/JobModal.vue'
import TaskModal from '@/components/NocturnScheduleModal/TaskModal.vue'
import NocturnJob from '@/models/nocturnJob.js'
import NocturnTask from '@/models/nocturnTask.js'
import useNocturnScheduleStore, { nocturnJobVisibilityKey } from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const nocturnScheduleStore = useNocturnScheduleStore()

const showModalForNewJob = ref(false)
const taskModalTarget = ref(null)
const taskModalIsNew = ref(false)

const jobs = computed<NocturnJob[]>(() => nocturnScheduleStore.allJobs)
const sharedWithMe = computed(() => nocturnScheduleStore.sharedWithMe)
const tasks = computed(() => nocturnScheduleStore.allTasks)

function openNewTask() {
	taskModalTarget.value = NocturnTask.createDefault()
	taskModalIsNew.value = true
}

function openEditTask(task) {
	taskModalTarget.value = task
	taskModalIsNew.value = false
}

async function toggleTask(task) {
	try {
		await nocturnScheduleStore.toggleTaskCompleted(task)
	} catch (error) {
		logger.error('Failed to update NocTurn task', { error })
		showError(t('nocturnkalender', 'Could not update task'))
	}
}

async function removeTask(task) {
	try {
		await nocturnScheduleStore.deleteTask(task.uid)
	} catch (error) {
		logger.error('Failed to delete NocTurn task', { error })
		showError(t('nocturnkalender', 'Could not delete task'))
	}
}

function sharedJobColor(entry) {
	return entry.shifts[0]?.color ?? '#999999'
}

function isSharedJobVisible(entry) {
	return nocturnScheduleStore.isJobVisible(nocturnJobVisibilityKey(entry.ownerId, entry.job.uid))
}

function toggleSharedJobVisibility(entry) {
	nocturnScheduleStore.toggleJobVisibility(nocturnJobVisibilityKey(entry.ownerId, entry.job.uid))
}

onMounted(async () => {
	try {
		await nocturnScheduleStore.loadAll()
	} catch (error) {
		logger.error('Failed to load NocTurn schedule', { error })
		showError(t('nocturnkalender', 'Could not load NocTurn shifts and rotations'))
	}

	try {
		await nocturnScheduleStore.loadSharedWithMe()
	} catch (error) {
		logger.error('Failed to load NocTurn jobs shared with the current user', { error })
		// Non-fatal: the user's own jobs above still loaded fine, shared
		// jobs are a bonus feature, not a reason to show an error banner.
	}
})
</script>

<template>
	<div class="nocturn-schedule-list">
		<AppNavigationCaption
			class="nocturn-schedule-list__caption"
			:name="t('nocturnkalender', 'NocTurn tasks')">
			<template #actions>
				<ActionButton :closeAfterClick="true" @click="openNewTask">
					<template #icon>
						<PlusIcon :size="20" decorative />
					</template>
					{{ t('nocturnkalender', 'Add task') }}
				</ActionButton>
			</template>
		</AppNavigationCaption>

		<div v-if="tasks.length === 0" class="nocturn-schedule-list__empty-tasks">
			{{ t('nocturnkalender', 'No tasks yet') }}
		</div>
		<div
			v-for="task in tasks"
			:key="task.uid"
			class="nocturn-schedule-list__task">
			<CheckboxMarked
				v-if="task.completed"
				:size="20"
				:fillColor="task.color"
				@click.prevent.stop="toggleTask(task)" />
			<CheckboxBlank
				v-else
				:size="20"
				:fillColor="task.color"
				@click.prevent.stop="toggleTask(task)" />
			<span
				class="nocturn-schedule-list__task-title"
				:class="{ 'nocturn-schedule-list__task-title--done': task.completed }"
				@click="openEditTask(task)">
				{{ task.title }}
			</span>
			<button class="nocturn-schedule-list__task-delete" :aria-label="t('nocturnkalender', 'Delete')" @click="removeTask(task)">
				<DeleteIcon :size="16" />
			</button>
		</div>

		<AppNavigationCaption
			class="nocturn-schedule-list__caption"
			:name="t('nocturnkalender', 'NocTurn shifts & rotations')">
			<template #actions>
				<ActionButton :closeAfterClick="true" @click="showModalForNewJob = true">
					<template #icon>
						<PlusIcon :size="20" decorative />
					</template>
					{{ t('nocturnkalender', 'Add job') }}
				</ActionButton>
			</template>
		</AppNavigationCaption>

		<NocturnJobItem v-for="job in jobs" :key="job.uid" :job="job" />

		<template v-if="sharedWithMe.length > 0">
			<AppNavigationCaption
				class="nocturn-schedule-list__caption"
				:name="t('nocturnkalender', 'Shared with me')" />

			<div
				v-for="entry in sharedWithMe"
				:key="`${entry.ownerId}-${entry.job.uid}`"
				class="nocturn-schedule-list__shared-job">
				<CheckboxMarked
					v-if="isSharedJobVisible(entry)"
					:size="20"
					:fillColor="sharedJobColor(entry)"
					@click.prevent.stop="toggleSharedJobVisibility(entry)" />
				<CheckboxBlank
					v-else
					:size="20"
					:fillColor="sharedJobColor(entry)"
					@click.prevent.stop="toggleSharedJobVisibility(entry)" />
				<NcAvatar :size="20" :user="entry.ownerId" />
				<span class="nocturn-schedule-list__shared-job-name">{{ entry.job.name }}</span>
				<span class="nocturn-schedule-list__shared-job-permission">
					{{ entry.permission === 'WRITE' ? t('nocturnkalender', 'can edit') : t('nocturnkalender', 'read-only') }}
				</span>
			</div>
		</template>

		<JobModal
			v-if="showModalForNewJob"
			:job="NocturnJob.createDefault()"
			:isNew="true"
			@close="showModalForNewJob = false" />
		<TaskModal
			v-if="taskModalTarget"
			:task="taskModalTarget"
			:isNew="taskModalIsNew"
			@close="taskModalTarget = null" />
	</div>
</template>

<style lang="scss" scoped>
.nocturn-schedule-list {
	&__shared-job {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 6px 12px 6px 44px;
		font-size: 0.95em;

		&-name {
			flex: 1;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		&-permission {
			color: var(--color-text-maxcontrast);
			font-size: 0.85em;
			flex-shrink: 0;
		}
	}

	&__empty-tasks {
		color: var(--color-text-maxcontrast);
		font-size: 0.9em;
		padding: 4px 12px 8px 44px;
	}

	&__task {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 4px 12px 4px 44px;
		font-size: 0.95em;

		&-title {
			flex: 1;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
			cursor: pointer;

			&--done {
				text-decoration: line-through;
				color: var(--color-text-maxcontrast);
			}
		}

		&-delete {
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
}
</style>
