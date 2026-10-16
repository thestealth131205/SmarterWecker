<!--
  - SPDX-FileCopyrightText: 2021 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: AGPL-3.0-or-later
-->
<script setup lang="ts">
import moment from '@nextcloud/moment'
import { computed } from 'vue'

const props = defineProps<{
	link: string
	confirmed: boolean
	start: number
	end: number
}>()

const startDate = computed<string>(() => moment(props.start * 1000).format('LLL'))
const endDate = computed<string>(() => moment(props.end * 1000).format('LLL'))
</script>

<template>
	<div class="guest-box">
		<div class="update">
			<div v-if="confirmed" class="confirmed">
				<h2>
					{{ $t('nocturnkalender', 'Thank you. Your booking from {startDate} to {endDate} has been confirmed.', { startDate: startDate, endDate: endDate }) }}
				</h2>
				{{ $t('nocturnkalender', 'Book another appointment:') }}
				<br>
				<a :href="link">{{ $t('nocturnkalender', 'See all available slots') }}</a>
			</div>
			<div v-else class="conflict">
				{{ $t('nocturnkalender', 'The slot for your appointment from {startDate} to {endDate} is not available any more.', { startDate: startDate, endDate: endDate }) }}
				<br>
				<br>
				{{ $t('nocturnkalender', 'Please book a different slot:') }}
				<br>
				<a :href="link">{{ $t('nocturnkalender', 'See all available slots') }}</a>
			</div>
		</div>
	</div>
</template>
