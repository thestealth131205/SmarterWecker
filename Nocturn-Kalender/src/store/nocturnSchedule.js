/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import { defineStore } from 'pinia'
import NocturnJob from '@/models/nocturnJob.js'
import NocturnRotation from '@/models/nocturnRotation.js'
import NocturnShift from '@/models/nocturnShift.js'
import {
	applyRotation as applyRotationApi,
	deleteJob as deleteJobApi,
	deleteRotation as deleteRotationApi,
	deleteShift as deleteShiftApi,
	fetchChanges,
	fetchJobShares,
	fetchSharedWithMe,
	saveJob as saveJobApi,
	saveRotation as saveRotationApi,
	saveShift as saveShiftApi,
	shareJob as shareJobApi,
	unshareJob as unshareJobApi,
} from '@/services/nocturnScheduleService.js'
import logger from '@/utils/logger.js'

export default defineStore('nocturnSchedule', {
	state: () => {
		return {
			jobs: {},
			shifts: {},
			// rotationUid -> NocturnRotation, days already merged in from rotationDays
			rotations: {},
			loaded: false,
			lastServerTime: 0,
			// Jobs other Nextcloud users shared with the current user:
			// [{ job: NocturnJob, ownerId, permission, shifts: NocturnShift[], rotations: NocturnRotation[] }]
			sharedWithMe: [],
		}
	},
	getters: {
		allJobs: (state) => Object.values(state.jobs).sort((a, b) => a.name.localeCompare(b.name)),
		shiftsForJob: (state) => (jobUid) => {
			return Object.values(state.shifts)
				.filter((shift) => shift.jobUid === jobUid)
				.sort((a, b) => (a.startHour * 60 + a.startMinute) - (b.startHour * 60 + b.startMinute))
		},
		rotationsForJob: (state) => (jobUid) => {
			return Object.values(state.rotations)
				.filter((rotation) => rotation.jobUid === jobUid)
				.sort((a, b) => a.name.localeCompare(b.name))
		},
	},
	actions: {
		/**
		 * Full initial sync. Safe to call multiple times - it just re-applies
		 * the delta since the last successful call.
		 */
		async loadAll() {
			try {
				const changes = await fetchChanges(this.lastServerTime)
				this.applyChanges(changes)
				this.loaded = true
			} catch (error) {
				logger.error('Failed to load NocTurn schedule', { error })
				throw error
			}
		},
		applyChanges(changes) {
			const rotationDaysByRotation = {}
			for (const day of changes.rotationDays ?? []) {
				if (!rotationDaysByRotation[day.rotationUid]) {
					rotationDaysByRotation[day.rotationUid] = []
				}
				rotationDaysByRotation[day.rotationUid].push(day)
			}

			for (const job of changes.jobs ?? []) {
				if (job.deletedAt) {
					delete this.jobs[job.uid]
				} else {
					this.jobs[job.uid] = new NocturnJob(job)
				}
			}

			for (const shift of changes.shifts ?? []) {
				if (shift.deletedAt) {
					delete this.shifts[shift.uid]
				} else {
					this.shifts[shift.uid] = new NocturnShift(shift)
				}
			}

			for (const rotation of changes.rotations ?? []) {
				if (rotation.deletedAt) {
					delete this.rotations[rotation.uid]
				} else {
					const days = (rotationDaysByRotation[rotation.uid] ?? [])
						.sort((a, b) => a.dayIndex - b.dayIndex)
						.map((day) => day.shiftUid)
					this.rotations[rotation.uid] = new NocturnRotation({ ...rotation, days })
				}
			}

			// Rotation day changes for rotations that weren't themselves part
			// of this delta (pattern edited, header untouched) still need to
			// update the already-loaded rotation's days.
			for (const [rotationUid, days] of Object.entries(rotationDaysByRotation)) {
				if (this.rotations[rotationUid] && !(changes.rotations ?? []).some((rotation) => rotation.uid === rotationUid)) {
					this.rotations[rotationUid].days = days
						.sort((a, b) => a.dayIndex - b.dayIndex)
						.map((day) => day.shiftUid)
				}
			}

			this.lastServerTime = changes.serverTime ?? this.lastServerTime
		},
		async saveJob(job) {
			try {
				const saved = await saveJobApi(job)
				this.jobs[saved.uid] = new NocturnJob(saved)
				return this.jobs[saved.uid]
			} catch (error) {
				logger.error('Failed to save NocTurn job', { error, job })
				throw error
			}
		},
		async deleteJob(uid) {
			try {
				await deleteJobApi(uid)
				delete this.jobs[uid]
			} catch (error) {
				logger.error('Failed to delete NocTurn job', { error, uid })
				throw error
			}
		},
		async saveShift(shift) {
			try {
				const saved = await saveShiftApi(shift)
				this.shifts[saved.uid] = new NocturnShift(saved)
				return this.shifts[saved.uid]
			} catch (error) {
				logger.error('Failed to save NocTurn shift', { error, shift })
				throw error
			}
		},
		async deleteShift(uid) {
			try {
				await deleteShiftApi(uid)
				delete this.shifts[uid]
			} catch (error) {
				logger.error('Failed to delete NocTurn shift', { error, uid })
				throw error
			}
		},
		async saveRotation(rotation) {
			try {
				const saved = await saveRotationApi(rotation)
				this.rotations[saved.uid] = new NocturnRotation({ ...saved, days: rotation.days })
				return this.rotations[saved.uid]
			} catch (error) {
				logger.error('Failed to save NocTurn rotation', { error, rotation })
				throw error
			}
		},
		async deleteRotation(uid) {
			try {
				await deleteRotationApi(uid)
				delete this.rotations[uid]
			} catch (error) {
				logger.error('Failed to delete NocTurn rotation', { error, uid })
				throw error
			}
		},
		/**
		 * @param {string} uid Rotation uid
		 * @param {number} startEpochDay Days since 1970-01-01 UTC
		 * @param {number} months How many months forward to generate
		 * @return {Promise<object>} `{fromEpochDay, toEpochDayExclusive, generatedDays}`
		 */
		async applyRotation(uid, startEpochDay, months) {
			try {
				return await applyRotationApi(uid, startEpochDay, months)
			} catch (error) {
				logger.error('Failed to apply NocTurn rotation', { error, uid })
				throw error
			}
		},
		/**
		 * @param {string} jobUid
		 * @param {string} sharedWithUserId
		 * @param {string} permission 'READ' or 'WRITE'
		 * @return {Promise<object>} Saved share as returned by the server
		 */
		async shareJob(jobUid, sharedWithUserId, permission) {
			try {
				return await shareJobApi(jobUid, sharedWithUserId, permission)
			} catch (error) {
				logger.error('Failed to share NocTurn job', { error, jobUid, sharedWithUserId })
				throw error
			}
		},
		/**
		 * @param {string} jobUid
		 * @param {string} sharedWithUserId
		 * @return {Promise<void>}
		 */
		async unshareJob(jobUid, sharedWithUserId) {
			try {
				await unshareJobApi(jobUid, sharedWithUserId)
			} catch (error) {
				logger.error('Failed to unshare NocTurn job', { error, jobUid, sharedWithUserId })
				throw error
			}
		},
		/**
		 * @param {string} jobUid
		 * @return {Promise<object[]>}
		 */
		async loadSharesForJob(jobUid) {
			try {
				return await fetchJobShares(jobUid)
			} catch (error) {
				logger.error('Failed to load NocTurn job shares', { error, jobUid })
				throw error
			}
		},
		/**
		 * Loads every job other Nextcloud users shared with the current
		 * user, into `sharedWithMe`.
		 */
		async loadSharedWithMe() {
			try {
				const entries = await fetchSharedWithMe()
				this.sharedWithMe = entries.map((entry) => ({
					job: new NocturnJob(entry.job),
					ownerId: entry.ownerId,
					permission: entry.permission,
					shifts: (entry.shifts ?? []).map((shift) => new NocturnShift(shift)),
					rotations: (entry.rotations ?? []).map((rotation) => new NocturnRotation(rotation)),
				}))
			} catch (error) {
				logger.error('Failed to load jobs shared with the current user', { error })
				throw error
			}
		},
	},
})
