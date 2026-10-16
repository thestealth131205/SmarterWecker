<!--
  - SPDX-FileCopyrightText: 2022 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<template>
	<NcFormBoxButton
		:label="$t('nocturnkalender', 'Attachments folder')"
		:description="attachmentsFolder"
		invertedAccent
		@click="selectCalendarFolder">
		<template #icon>
			<IconFolderOpen :size="20" />
		</template>
	</NcFormBoxButton>
</template>

<script>
import { getFilePickerBuilder, showError, showSuccess } from '@nextcloud/dialogs'
import { NcFormBoxButton } from '@nextcloud/vue'
import debounce from 'debounce'
import { mapState, mapStores } from 'pinia'
import IconFolderOpen from 'vue-material-design-icons/FolderOpenOutline.vue'
import usePrincipalsStore from '@/store/principals.js'
import useSettingsStore from '@/store/settings.js'
import logger from '@/utils/logger.js'

export default {
	name: 'SettingsAttachmentsFolder',

	components: {
		NcFormBoxButton,
		IconFolderOpen,
	},

	computed: {
		...mapStores(useSettingsStore, usePrincipalsStore),
		...mapState(useSettingsStore, {
			attachmentsFolder: (store) => store.attachmentsFolder || '/',
		}),
	},

	methods: {
		async selectCalendarFolder() {
			const userId = this.principalsStore.getCurrentUserPrincipal.dav.userId
			const picker = getFilePickerBuilder(t('nocturnkalender', 'Select the default location for attachments'))
				.setMultiSelect(false)
				.addButton({
					label: t('nocturnkalender', 'Pick'),
					type: 'primary',
					callback: (nodes) => logger.debug('Picked attachment folder', { nodes }),
				})
				.addMimeTypeFilter('httpd/unix-directory')
				.allowDirectories()
				// Exclude folders not owned by the current user
				.setFilter((node) => node.owner === userId)
				.build()
			const path = await picker.pick()
			this.saveAttachmentsFolder(path)
		},

		debounceSaveAttachmentsFolder: debounce(function(...args) {
			this.saveAttachmentsFolder(...args)
		}, 300),

		saveAttachmentsFolder(path) {
			if (typeof path !== 'string' || path.trim() === '' || !path.startsWith('/')) {
				showError(t('nocturnkalender', 'Invalid location selected'))
				return
			}

			if (path.includes('//')) {
				path = path.replace(/\/\//gi, '/')
			}

			this.settingsStore.setAttachmentsFolder({ attachmentsFolder: path })
				.then(() => {
					showSuccess(this.$t('nocturnkalender', 'Attachments folder successfully saved.'))
				})
				.catch((error) => {
					logger.error(error)
					showError(this.$t('nocturnkalender', 'Error on saving attachments folder.'))
				})
		},

	},
}
</script>
