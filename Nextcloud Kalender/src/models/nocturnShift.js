/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

/**
 * A single named, colored shift definition belonging to a job, e.g.
 * "Frühschicht" 06:00-14:00. Mirrors the server entity in
 * lib/Db/NocturnShift.php.
 *
 * @class
 */
export default class NocturnShift {

	constructor(data = {}) {
		/** @member {?string} */
		this.uid = data.uid ?? null
		/** @member {string} */
		this.jobUid = data.jobUid ?? ''
		/** @member {string} */
		this.name = data.name ?? ''
		/** @member {string} */
		this.color = data.color ?? '#FF9800'
		/** @member {number} */
		this.startHour = data.startHour ?? 6
		/** @member {number} */
		this.startMinute = data.startMinute ?? 0
		/** @member {number} */
		this.durationMinutes = data.durationMinutes ?? 480
		/** @member {number} */
		this.earliestWakeOffsetMinutes = data.earliestWakeOffsetMinutes ?? 0
		/** @member {number} */
		this.latestWakeupOffsetMinutes = data.latestWakeupOffsetMinutes ?? 0
		/** @member {?number} */
		this.createdAt = data.createdAt ?? null
		/** @member {?number} */
		this.updatedAt = data.updatedAt ?? null
		/** @member {?number} */
		this.deletedAt = data.deletedAt ?? null
	}

	/**
	 * @param {string} jobUid Job the new shift belongs to
	 * @return {NocturnShift}
	 */
	static createDefault(jobUid) {
		return new NocturnShift({ jobUid, name: '', color: '#FF9800', startHour: 6, startMinute: 0, durationMinutes: 480 })
	}

	/**
	 * @return {string} "HH:MM" of the shift's end, wrapping past midnight
	 */
	get endTimeLabel() {
		const totalStart = this.startHour * 60 + this.startMinute
		const totalEnd = (totalStart + this.durationMinutes) % (24 * 60)
		const hour = Math.floor(totalEnd / 60)
		const minute = totalEnd % 60
		return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
	}

	/**
	 * @return {string} "HH:MM" of the shift's start
	 */
	get startTimeLabel() {
		return `${String(this.startHour).padStart(2, '0')}:${String(this.startMinute).padStart(2, '0')}`
	}

	/**
	 * @return {NocturnShift}
	 */
	clone() {
		return new NocturnShift({ ...this })
	}
}
