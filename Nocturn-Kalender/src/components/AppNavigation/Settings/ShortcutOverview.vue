<!--
  - SPDX-FileCopyrightText: 2020 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->
<template>
	<NcAppSettingsShortcutsSection>
		<NcHotkeyList
			v-for="category in shortcuts"
			:key="category.categoryId"
			:label="category.categoryLabel">
			<NcHotkey
				v-for="(shortcut, index) in category.shortcuts"
				:key="`${category.categoryId}-${index}`"
				:hotkey="shortcut.or ? null : shortcut.keys[0]"
				:label="shortcut.label">
				<template v-if="shortcut.or" #hotkey>
					<div class="shortcut-section-item__keys">
						<div v-for="(key, keyIndex) in shortcut.keys" :key="keyIndex">
							<NcKbd :symbol="key" />
							<span
								v-if="keyIndex !== (shortcut.keys.length - 1)">
								{{ $t('nocturnkalender', 'or') }}
							</span>
						</div>
					</div>
				</template>
			</NcHotkey>
		</NcHotkeyList>
	</NcAppSettingsShortcutsSection>
</template>

<script>
import { translate as t } from '@nextcloud/l10n'
import { NcAppSettingsShortcutsSection, NcHotkey, NcHotkeyList } from '@nextcloud/vue'
import NcKbd from '@nextcloud/vue/components/NcKbd'

export default {
	name: 'ShortcutOverview',

	components: {
		NcAppSettingsShortcutsSection,
		NcHotkeyList,
		NcHotkey,
		NcKbd,
	},

	computed: {
		shortcuts() {
			return [{
				categoryId: 'navigation',
				categoryLabel: t('nocturnkalender', 'Navigation'),
				shortcuts: [{
					keys: ['P', 'K'],
					label: t('nocturnkalender', 'Previous period'),
					or: true,
				}, {
					keys: ['N', 'J'],
					label: t('nocturnkalender', 'Next period'),
					or: true,
				}, {
					keys: ['T'],
					label: t('nocturnkalender', 'Today'),
					or: true,
				}],
			}, {
				categoryId: 'views',
				categoryLabel: t('nocturnkalender', 'Views'),
				shortcuts: [{
					keys: ['1', 'D'],
					label: t('nocturnkalender', 'Day view'),
					or: true,
				}, {
					keys: ['2', 'W'],
					label: t('nocturnkalender', 'Week view'),
					or: true,
				}, {
					keys: ['3', 'M'],
					label: t('nocturnkalender', 'Month view'),
					or: true,
				}, {
					keys: ['4', 'Y'],
					label: t('nocturnkalender', 'Year view'),
					or: true,
				}, {
					keys: ['5', 'L'],
					label: t('nocturnkalender', 'List view'),
					or: true,
				}],
			}, {
				categoryId: 'actions',
				categoryLabel: t('nocturnkalender', 'Actions'),
				shortcuts: [{
					keys: ['C'],
					label: t('nocturnkalender', 'Create event'),
				}, {
					keys: ['H'],
					label: t('nocturnkalender', 'Show shortcuts'),
				}],
			}, {
				categoryId: 'editor',
				categoryLabel: t('nocturnkalender', 'Editor'),
				shortcuts: [{
					keys: ['Escape'],
					label: t('nocturnkalender', 'Close editor'),
				}, {
					keys: ['Control Enter'],
					label: t('nocturnkalender', 'Save edited event'),
				}, {
					keys: ['Control Delete'],
					label: t('nocturnkalender', 'Delete edited event'),
				}, {
					keys: ['Control D'],
					label: t('nocturnkalender', 'Duplicate event'),
				}],
			}]
		},
	},
}
</script>

<style lang="scss" scoped>
.shortcut-section-item__keys {
	display: flex;
	gap: var(--default-grid-baseline);
}
</style>
