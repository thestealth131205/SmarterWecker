<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import { showError } from '@nextcloud/dialogs'
import { t } from '@nextcloud/l10n'
import { NcActionButton as ActionButton, NcAppNavigationCaption as AppNavigationCaption, NcAvatar } from '@nextcloud/vue'
import { computed, onMounted, ref } from 'vue'
import PlusIcon from 'vue-material-design-icons/Plus.vue'
import NocturnJobItem from '@/components/AppNavigation/NocturnScheduleList/NocturnJobItem.vue'
import JobModal from '@/components/NocturnScheduleModal/JobModal.vue'
import NocturnJob from '@/models/nocturnJob.js'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const nocturnScheduleStore = useNocturnScheduleStore()

const showModalForNewJob = ref(false)

const jobs = computed<NocturnJob[]>(() => nocturnScheduleStore.allJobs)
const sharedWithMe = computed(() => nocturnScheduleStore.sharedWithMe)

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
}
</style>
