/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

/**
 * A single weekly timetable period belonging to a student-mode job, e.g.
 * "Mathe" every Monday 07:45-08:30. Mirrors the server entity in
 * lib/Db/NocturnLesson.php.
 *
 * @class
 */
export default class NocturnLesson {

	static WEEKDAY_LABELS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su']

	constructor(data = {}) {
		/** @member {?string} */
		this.uid = data.uid ?? null
		/** @member {string} */
		this.jobUid = data.jobUid ?? ''
		/** @member {number} 0 = Monday .. 6 = Sunday */
		this.weekday = data.weekday ?? 0
		/** @member {string} */
		this.name = data.name ?? ''
		/** @member {string} */
		this.color = data.color ?? '#2196F3'
		/** @member {number} */
		this.startHour = data.startHour ?? 8
		/** @member {number} */
		this.startMinute = data.startMinute ?? 0
		/** @member {number} */
		this.durationMinutes = data.durationMinutes ?? 45
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
	 * @param {string} jobUid Job the new lesson belongs to
	 * @param {number} weekday 0 = Monday .. 6 = Sunday
	 * @return {NocturnLesson}
	 */
	static createDefault(jobUid, weekday = 0) {
		return new NocturnLesson({ jobUid, weekday, name: '', color: '#2196F3', startHour: 8, startMinute: 0, durationMinutes: 45 })
	}

	/**
	 * @return {string} "HH:MM" of the lesson's end, wrapping past midnight
	 */
	get endTimeLabel() {
		const totalStart = this.startHour * 60 + this.startMinute
		const totalEnd = (totalStart + this.durationMinutes) % (24 * 60)
		const hour = Math.floor(totalEnd / 60)
		const minute = totalEnd % 60
		return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
	}

	/**
	 * @return {string} "HH:MM" of the lesson's start
	 */
	get startTimeLabel() {
		return `${String(this.startHour).padStart(2, '0')}:${String(this.startMinute).padStart(2, '0')}`
	}

	/**
	 * @return {NocturnLesson}
	 */
	clone() {
		return new NocturnLesson({ ...this })
	}
}
