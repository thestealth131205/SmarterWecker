/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import { fetchOwnOccurrences, fetchSharedOccurrences } from '@/services/nocturnScheduleService.js'
import logger from '@/utils/logger.js'

const MS_PER_DAY = 24 * 60 * 60 * 1000

/**
 * FullCalendar hands us Date objects whose UTC getters reflect the
 * wall-clock date in the calendar's configured timeZone (not necessarily
 * the browser's local zone) - so reading the date via the UTC getters and
 * re-deriving a UTC-midnight timestamp from them gives the calendar-date
 * (timezone-agnostic) epoch day NocTurn's occurrences are keyed by.
 *
 * @param {Date} date
 * @return {number}
 */
function epochDayFromCalendarDate(date) {
	const utcMidnight = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
	return Math.floor(utcMidnight / MS_PER_DAY)
}

/**
 * @param {number} epochDay
 * @return {string} ISO date ("YYYY-MM-DD"), safe to use as a FullCalendar
 *                   all-day event's start/end without timezone ambiguity
 */
function isoDateFromEpochDay(epochDay) {
	return new Date(epochDay * MS_PER_DAY).toISOString().slice(0, 10)
}

/**
 * Builds one FullCalendar event source per visible NocTurn job (own or
 * shared-with-me) - renders the job's calendar occurrences (shifts
 * generated from a rotation, or entered manually) as colored all-day
 * events. Deliberately independent of the CalDAV-backed `eventSource.js`
 * pipeline: NocTurn's rows are plain JSON from the NocTurn REST API, not
 * iCal VEVENTs, so there is no `calendarObjects`/ICAL.js parsing step here.
 *
 * @return {(entry: {key: string, jobUid: string, ownerId: string|null, job: object, shifts: object[], editable: boolean}) => object}
 */
export default function() {
	return function(entry) {
		const shiftsByUid = {}
		for (const shift of entry.shifts) {
			shiftsByUid[shift.uid] = shift
		}

		return {
			id: `nocturn-${entry.key}`,
			backgroundColor: 'transparent',
			borderColor: 'transparent',
			editable: false,
			events: async ({ start, end }, successCallback, failureCallback) => {
				const fromEpochDay = epochDayFromCalendarDate(start)
				const toEpochDayExclusive = epochDayFromCalendarDate(end)

				try {
					const occurrences = entry.ownerId
						? await fetchSharedOccurrences(entry.ownerId, entry.jobUid, fromEpochDay, toEpochDayExclusive)
						: await fetchOwnOccurrences(entry.jobUid, fromEpochDay, toEpochDayExclusive)

					const events = occurrences
						.filter((occurrence) => !!occurrence.shiftUid)
						.map((occurrence) => {
							const shift = shiftsByUid[occurrence.shiftUid]
							const color = shift?.color ?? '#999999'
							const isoDate = isoDateFromEpochDay(occurrence.epochDay)

							return {
								id: `nocturn-${entry.key}-${occurrence.uid}`,
								title: shift ? `${entry.job.name}: ${shift.name}` : entry.job.name,
								start: isoDate,
								end: isoDate,
								allDay: true,
								backgroundColor: color,
								borderColor: color,
								editable: false,
								extendedProps: {
									isNocturnOccurrence: true,
									jobUid: entry.jobUid,
									ownerId: entry.ownerId,
									shiftUid: occurrence.shiftUid,
								},
							}
						})

					successCallback(events)
				} catch (error) {
					logger.error('Failed to load NocTurn occurrences for the calendar grid', { error, entry })
					failureCallback(error)
				}
			},
		}
	}
}
