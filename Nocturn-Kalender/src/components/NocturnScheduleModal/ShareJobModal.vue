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
	NcActions as Actions,
	NcAvatar,
	NcCheckboxRadioSwitch,
	NcModal,
	NcSelect,
} from '@nextcloud/vue'
import debounce from 'debounce'
import { onMounted, ref } from 'vue'
import DeleteIcon from 'vue-material-design-icons/TrashCanOutline.vue'
import NocturnJobShare from '@/models/nocturnJobShare.js'
import { principalPropertySearchByDisplaynameOrEmail } from '@/services/caldavService.js'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const props = defineProps<{
	job: NocturnJob
}>()

const emit = defineEmits<{
	close: []
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const isLoading = ref(false)
const shares = ref<NocturnJobShare[]>([])
const searchResults = ref<object[]>([])
const isSearching = ref(false)
const newSharePermission = ref<string>(NocturnJobShare.PERMISSION_READ)

onMounted(async () => {
	await reloadShares()
})

async function reloadShares(): Promise<void> {
	try {
		const raw = await nocturnScheduleStore.loadSharesForJob(props.job.uid)
		shares.value = raw.map((share) => new NocturnJobShare(share))
	} catch (error) {
		logger.error('Failed to load NocTurn job shares', { error })
		showError(t('nocturnkalender', 'Could not load shares for this job'))
	}
}

/**
 * Searches Nextcloud users by display name or email - circles, groups and
 * remote/federated users are intentionally excluded: a NocTurn job is
 * shared with exactly one other person's own schedule view, not fanned out
 * to a whole group.
 */
const findUsers = debounce(async function(query: string) {
	if (!query) {
		searchResults.value = []
		return
	}
	isSearching.value = true
	try {
		const alreadySharedWith = new Set(shares.value.map((share) => share.sharedWith))
		const results = await principalPropertySearchByDisplaynameOrEmail(query)
		searchResults.value = results
			.filter((result) => result.calendarUserType === 'INDIVIDUAL')
			.map((result) => ({ userId: result.userId, displayName: result.displayname, email: result.email }))
			.filter((candidate) => candidate.userId && candidate.userId !== getCurrentUserId() && !alreadySharedWith.has(candidate.userId))
	} catch (error) {
		logger.error('Failed to search for users to share with', { error })
		searchResults.value = []
	} finally {
		isSearching.value = false
	}
}, 500)

function getCurrentUserId(): string | null {
	return (window as unknown as { OC?: { getCurrentUser?: () => { uid: string } } }).OC?.getCurrentUser?.()?.uid ?? null
}

async function addShare(candidate: { userId: string }): Promise<void> {
	if (!candidate) {
		return
	}
	isLoading.value = true
	try {
		await nocturnScheduleStore.shareJob(props.job.uid, candidate.userId, newSharePermission.value)
		await reloadShares()
	} catch (error) {
		logger.error('Failed to share NocTurn job', { error })
		showError(t('nocturnkalender', 'Could not share this job'))
	} finally {
		isLoading.value = false
		searchResults.value = []
	}
}

async function updatePermission(share: NocturnJobShare, isWriteable: boolean): Promise<void> {
	try {
		await nocturnScheduleStore.shareJob(props.job.uid, share.sharedWith, isWriteable ? NocturnJobShare.PERMISSION_WRITE : NocturnJobShare.PERMISSION_READ)
		await reloadShares()
	} catch (error) {
		logger.error('Failed to update NocTurn job share permission', { error })
		showError(t('nocturnkalender', 'Could not update this share'))
	}
}

async function removeShare(share: NocturnJobShare): Promise<void> {
	try {
		await nocturnScheduleStore.unshareJob(props.job.uid, share.sharedWith)
		shares.value = shares.value.filter((existing) => existing.sharedWith !== share.sharedWith)
	} catch (error) {
		logger.error('Failed to unshare NocTurn job', { error })
		showError(t('nocturnkalender', 'Could not remove this share'))
	}
}
</script>

<template>
	<NcModal
		size="small"
		:name="t('nocturnkalender', 'Share “{name}”', { name: job.name })"
		@close="$emit('close')">
		<div class="nocturn-share-job-modal">
			<h2>{{ t('nocturnkalender', 'Share “{name}”', { name: job.name }) }}</h2>
			<p class="nocturn-share-job-modal__hint">
				{{ t('nocturnkalender', 'Lets another Nextcloud user see this job’s shifts, rotations and calendar entries from their own NocTurn shifts & rotations list - read-only, or read/write so they can enter shift swaps themselves.') }}
			</p>

			<NcSelect
				class="nocturn-share-job-modal__search"
				:options="searchResults"
				:searchable="true"
				:internalSearch="false"
				:loading="isSearching"
				label="displayName"
				trackBy="userId"
				:placeholder="t('nocturnkalender', 'Share with user …')"
				:clearable="false"
				@search="findUsers"
				@option:selected="addShare">
				<template #no-options>
					<span>{{ t('nocturnkalender', 'No users found') }}</span>
				</template>
				<template #option="candidate">
					<div class="nocturn-share-job-modal__option">
						<NcAvatar :user="candidate.userId" :displayName="candidate.displayName" />
						<div class="nocturn-share-job-modal__option-label">
							{{ candidate.displayName }}
							<p>{{ candidate.email }}</p>
						</div>
					</div>
				</template>
			</NcSelect>

			<NcCheckboxRadioSwitch
				class="nocturn-share-job-modal__default-permission"
				:modelValue="newSharePermission === 'WRITE'"
				@update:modelValue="newSharePermission = $event ? 'WRITE' : 'READ'">
				{{ t('nocturnkalender', 'New shares can edit (enter shift swaps)') }}
			</NcCheckboxRadioSwitch>

			<div v-if="shares.length === 0" class="nocturn-share-job-modal__empty">
				{{ t('nocturnkalender', 'Not shared with anyone yet') }}
			</div>

			<div
				v-for="share in shares"
				:key="share.sharedWith"
				class="nocturn-share-job-modal__share">
				<NcAvatar :user="share.sharedWith" />
				<span class="nocturn-share-job-modal__share-name">{{ share.sharedWith }}</span>
				<NcCheckboxRadioSwitch
					:modelValue="share.permission === 'WRITE'"
					:disabled="isLoading"
					@update:modelValue="updatePermission(share, $event)">
					{{ t('nocturnkalender', 'can edit') }}
				</NcCheckboxRadioSwitch>
				<Actions>
					<ActionButton :closeAfterClick="true" @click="removeShare(share)">
						<template #icon>
							<DeleteIcon :size="20" />
						</template>
						{{ t('nocturnkalender', 'Unshare') }}
					</ActionButton>
				</Actions>
			</div>
		</div>
	</NcModal>
</template>

<style lang="scss" scoped>
.nocturn-share-job-modal {
	padding: 12px 20px 20px;

	&__hint {
		color: var(--color-text-maxcontrast);
		margin-bottom: 12px;
	}

	&__search {
		margin-bottom: 12px;
	}

	&__default-permission {
		margin-bottom: 16px;
	}

	&__empty {
		color: var(--color-text-maxcontrast);
		font-size: 0.9em;
		padding: 8px 0;
	}

	&__option {
		display: flex;
		align-items: center;
		gap: 10px;
		width: 100%;

		&-label {
			flex: 1 auto;
			flex-direction: column;

			p {
				color: var(--color-text-lighter);
				line-height: 1;
			}
		}
	}

	&__share {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 6px 0;
		border-top: 1px solid var(--color-border);

		&-name {
			flex: 1;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}
	}
}
</style>
