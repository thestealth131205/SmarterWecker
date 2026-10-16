<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<script setup lang="ts">
import { t } from '@nextcloud/l10n'
import {
	NcButton,
	NcCheckboxRadioSwitch,
	NcModal,
	NcTextField,
} from '@nextcloud/vue'
import { ref } from 'vue'
import CheckIcon from 'vue-material-design-icons/Check.vue'
import NocturnJob from '@/models/nocturnJob.js'
import useNocturnScheduleStore from '@/store/nocturnSchedule.js'
import logger from '@/utils/logger.js'

const props = defineProps<{
	job: NocturnJob
	isNew: boolean
}>()

const emit = defineEmits<{
	close: []
}>()

const nocturnScheduleStore = useNocturnScheduleStore()

const isLoading = ref(false)
const editing = ref<NocturnJob>(props.job.clone())

const formTitle = props.isNew ? t('calendar', 'Create job') : t('calendar', 'Edit job')
const saveButtonText = props.isNew ? t('calendar', 'Create') : t('calendar', 'Save')

async function save(): Promise<void> {
	isLoading.value = true
	try {
		await nocturnScheduleStore.saveJob(editing.value)
		emit('close')
	} catch (error) {
		logger.error('Failed to save NocTurn job', { error })
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
		<div class="nocturn-job-modal">
			<h2>{{ formTitle }}</h2>
			<NcTextField
				v-model="editing.name"
				class="nocturn-job-modal__row"
				:label="t('calendar', 'Name')"
				:placeholder="t('calendar', 'e.g. Main job, school, ...')" />
			<NcCheckboxRadioSwitch
				class="nocturn-job-modal__row"
				:modelValue="editing.mode === 'STUDENT'"
				@update:modelValue="editing.mode = $event ? 'STUDENT' : 'SHIFT'">
				{{ t('calendar', 'Student mode (timetable instead of shifts)') }}
			</NcCheckboxRadioSwitch>
			<NcButton
				class="nocturn-job-modal__submit"
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
.nocturn-job-modal {
	padding: 12px 20px 20px;

	&__row {
		margin-bottom: 12px;
	}

	&__submit {
		margin-top: 8px;
	}
}
</style>
