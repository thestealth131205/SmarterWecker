<!--
  - SPDX-FileCopyrightText: 2019 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->

<template>
	<NcSelect
		v-model="selected"
		:allowEmpty="false"
		:options="options"
		:disabled="disabled"
		:clearable="false"
		:ariaLabelCombobox="$t('nocturnkalender', 'Time unit')"
		inputId="alarm-time-unit"
		label="label" />
</template>

<script>
import { NcSelect } from '@nextcloud/vue'

export default {
	name: 'AlarmTimeUnitSelect',
	components: {
		NcSelect,
	},

	props: {
		unit: {
			type: String,
			required: true,
		},

		isAllDay: {
			type: Boolean,
			required: true,
		},

		disabled: {
			type: Boolean,
			required: true,
		},
	},

	emits: ['change'],

	computed: {
		/**
		 * This returns a list of options for the unit select.
		 * We offer seconds only when we have to, not by default
		 *
		 * In All-day mode, we only offer days and weeks.
		 *
		 * @return {object[]}
		 */
		options() {
			const options = []

			if (this.unit === 'seconds') {
				options.push({
					label: this.$t('nocturnkalender', 'seconds'),
					unit: 'seconds',
				})
			}

			if (!this.isAllDay || ['minutes', 'hours'].indexOf(this.unit) !== -1) {
				options.push({
					label: this.$t('nocturnkalender', 'minutes'),
					unit: 'minutes',
				})
				options.push({
					label: this.$t('nocturnkalender', 'hours'),
					unit: 'hours',
				})
			}

			options.push({
				label: this.$t('nocturnkalender', 'days'),
				unit: 'days',
			})
			options.push({
				label: this.$t('nocturnkalender', 'weeks'),
				unit: 'weeks',
			})

			return options
		},

		/**
		 * This is the selected option
		 *
		 * @return {object}
		 */
		selected: {
			get() {
				return this.options.find((o) => o.unit === this.unit)
			},

			set(value) {
				if (!value) {
					return
				}

				this.$emit('change', value.unit)
			},
		},
	},
}
</script>
