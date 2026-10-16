/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import axios from '@nextcloud/axios'
import { generateUrl } from '@nextcloud/router'
import logger from '@/utils/logger.js'

/**
 * Incremental sync: returns every job/shift/rotation/rotation day/occurrence
 * that changed (including soft-deleted tombstones) since the given server
 * timestamp. Pass 0 for a full initial sync.
 *
 * @param {number} since Server timestamp (ms) of the last sync, or 0
 * @return {Promise<object>}
 */
export async function fetchChanges(since = 0) {
	logger.debug('Fetching NocTurn schedule changes', { since })
	const url = generateUrl('/apps/calendar/v1/nocturn/sync')

	const response = await axios.get(url, { params: { since } })
	return response.data.data
}

/**
 * @param {import('@/models/nocturnJob.js').default} job
 * @return {Promise<object>} Saved job as returned by the server
 */
export async function saveJob(job) {
	logger.debug('Saving NocTurn job', { job })
	const url = generateUrl('/apps/calendar/v1/nocturn/jobs')

	const response = await axios.post(url, {
		uid: job.uid,
		name: job.name,
		mode: job.mode,
		updatedAt: job.updatedAt,
	})
	return response.data.data
}

/**
 * @param {string} uid
 * @return {Promise<void>}
 */
export async function deleteJob(uid) {
	logger.debug('Deleting NocTurn job', { uid })
	const url = generateUrl('/apps/calendar/v1/nocturn/jobs/{uid}', { uid })

	await axios.delete(url)
}

/**
 * @param {import('@/models/nocturnShift.js').default} shift
 * @return {Promise<object>} Saved shift as returned by the server
 */
export async function saveShift(shift) {
	logger.debug('Saving NocTurn shift', { shift })
	const url = generateUrl('/apps/calendar/v1/nocturn/shifts')

	const response = await axios.post(url, {
		uid: shift.uid,
		jobUid: shift.jobUid,
		name: shift.name,
		color: shift.color,
		startHour: shift.startHour,
		startMinute: shift.startMinute,
		durationMinutes: shift.durationMinutes,
		earliestWakeOffsetMinutes: shift.earliestWakeOffsetMinutes,
		latestWakeupOffsetMinutes: shift.latestWakeupOffsetMinutes,
		updatedAt: shift.updatedAt,
	})
	return response.data.data
}

/**
 * @param {string} uid
 * @return {Promise<void>}
 */
export async function deleteShift(uid) {
	logger.debug('Deleting NocTurn shift', { uid })
	const url = generateUrl('/apps/calendar/v1/nocturn/shifts/{uid}', { uid })

	await axios.delete(url)
}

/**
 * @param {import('@/models/nocturnRotation.js').default} rotation
 * @return {Promise<object>} Saved rotation as returned by the server
 */
export async function saveRotation(rotation) {
	logger.debug('Saving NocTurn rotation', { rotation })
	const url = generateUrl('/apps/calendar/v1/nocturn/rotations')

	const response = await axios.post(url, {
		uid: rotation.uid,
		jobUid: rotation.jobUid,
		name: rotation.name,
		days: rotation.days,
		updatedAt: rotation.updatedAt,
	})
	return response.data.data
}

/**
 * @param {string} uid
 * @return {Promise<void>}
 */
export async function deleteRotation(uid) {
	logger.debug('Deleting NocTurn rotation', { uid })
	const url = generateUrl('/apps/calendar/v1/nocturn/rotations/{uid}', { uid })

	await axios.delete(url)
}

/**
 * Generates (or re-generates) the calendar occurrences for a rotation
 * template over a date range, repeating its pattern.
 *
 * @param {string} uid Rotation uid
 * @param {number} startEpochDay Days since 1970-01-01 UTC
 * @param {number} months How many months forward to generate
 * @return {Promise<object>} `{fromEpochDay, toEpochDayExclusive, generatedDays}`
 */
export async function applyRotation(uid, startEpochDay, months) {
	logger.debug('Applying NocTurn rotation', { uid, startEpochDay, months })
	const url = generateUrl('/apps/calendar/v1/nocturn/rotations/{uid}/apply', { uid })

	const response = await axios.post(url, { startEpochDay, months })
	return response.data.data
}

/**
 * Grants (or updates) another Nextcloud user read or read/write access to a
 * job's full schedule - the NocTurn equivalent of sharing a calendar.
 *
 * @param {string} jobUid
 * @param {string} sharedWithUserId
 * @param {string} permission 'READ' or 'WRITE'
 * @return {Promise<object>} Saved share as returned by the server
 */
export async function shareJob(jobUid, sharedWithUserId, permission) {
	logger.debug('Sharing NocTurn job', { jobUid, sharedWithUserId, permission })
	const url = generateUrl('/apps/calendar/v1/nocturn/jobs/{uid}/share', { uid: jobUid })

	const response = await axios.post(url, { sharedWithUserId, permission })
	return response.data.data
}

/**
 * @param {string} jobUid
 * @param {string} sharedWithUserId
 * @return {Promise<void>}
 */
export async function unshareJob(jobUid, sharedWithUserId) {
	logger.debug('Unsharing NocTurn job', { jobUid, sharedWithUserId })
	const url = generateUrl('/apps/calendar/v1/nocturn/jobs/{uid}/share/{sharedWithUserId}', { uid: jobUid, sharedWithUserId })

	await axios.delete(url)
}

/**
 * @param {string} jobUid
 * @return {Promise<object[]>}
 */
export async function fetchJobShares(jobUid) {
	logger.debug('Fetching NocTurn job shares', { jobUid })
	const url = generateUrl('/apps/calendar/v1/nocturn/jobs/{uid}/shares', { uid: jobUid })

	const response = await axios.get(url)
	return response.data.data
}

/**
 * Every job (plus its shifts/rotations) another Nextcloud user has shared
 * with the current user.
 *
 * @return {Promise<object[]>}
 */
export async function fetchSharedWithMe() {
	logger.debug('Fetching NocTurn jobs shared with the current user')
	const url = generateUrl('/apps/calendar/v1/nocturn/shared-with-me')

	const response = await axios.get(url)
	return response.data.data
}

/**
 * Occurrences of a job shared with the current user, within a date range.
 *
 * @param {string} ownerId
 * @param {string} jobUid
 * @param {number} fromEpochDay
 * @param {number} toEpochDayExclusive
 * @return {Promise<object[]>}
 */
export async function fetchSharedOccurrences(ownerId, jobUid, fromEpochDay, toEpochDayExclusive) {
	logger.debug('Fetching shared NocTurn occurrences', { ownerId, jobUid, fromEpochDay, toEpochDayExclusive })
	const url = generateUrl('/apps/calendar/v1/nocturn/shared/{ownerId}/jobs/{jobUid}/occurrences', { ownerId, jobUid })

	const response = await axios.get(url, { params: { fromEpochDay, toEpochDayExclusive } })
	return response.data.data
}

/**
 * Lets a WRITE-permission collaborator enter/edit a single day's shift on
 * someone else's shared job.
 *
 * @param {string} ownerId
 * @param {string} jobUid
 * @param {object} occurrence `{uid, epochDay, shiftUid, updatedAt}`
 * @return {Promise<object>}
 */
export async function saveSharedOccurrence(ownerId, jobUid, occurrence) {
	logger.debug('Saving shared NocTurn occurrence', { ownerId, jobUid, occurrence })
	const url = generateUrl('/apps/calendar/v1/nocturn/shared/{ownerId}/jobs/{jobUid}/occurrences', { ownerId, jobUid })

	const response = await axios.post(url, {
		uid: occurrence.uid,
		epochDay: occurrence.epochDay,
		shiftUid: occurrence.shiftUid,
		updatedAt: occurrence.updatedAt,
	})
	return response.data.data
}

/**
 * @param {string} ownerId
 * @param {string} jobUid
 * @param {string} uid
 * @return {Promise<void>}
 */
export async function deleteSharedOccurrence(ownerId, jobUid, uid) {
	logger.debug('Deleting shared NocTurn occurrence', { ownerId, jobUid, uid })
	const url = generateUrl('/apps/calendar/v1/nocturn/shared/{ownerId}/jobs/{jobUid}/occurrences/{uid}', { ownerId, jobUid, uid })

	await axios.delete(url)
}
