<?php

declare(strict_types=1);
/**
 * SPDX-FileCopyrightText: 2016-2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-FileCopyrightText: 2014-2016 ownCloud, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
return [
	'routes' => [
		// User views
		['name' => 'view#index', 'url' => '/', 'verb' => 'GET'],
		['name' => 'view#index', 'url' => '/new', 'verb' => 'GET', 'postfix' => 'direct.new'],
		['name' => 'view#index', 'url' => '/new/{isAllDay}/{dtStart}/{dtEnd}', 'verb' => 'GET', 'postfix' => 'direct.new.timerange'],
		['name' => 'view#index', 'url' => '/edit/{objectId}', 'verb' => 'GET', 'postfix' => 'direct.edit'],
		['name' => 'view#index', 'url' => '/edit/{objectId}/{recurrenceId}', 'verb' => 'GET', 'postfix' => 'direct.edit.recurrenceId'],
		['name' => 'view#index', 'url' => '/{view}/{timeRange}', 'verb' => 'GET', 'requirements' => ['view' => 'timeGridDay|timeGridWeek|dayGridMonth|multiMonthYear|listMonth'], 'postfix' => 'view.timerange'],
		['name' => 'view#index', 'url' => '/{view}/{timeRange}/new/{mode}/{isAllDay}/{dtStart}/{dtEnd}', 'verb' => 'GET', 'requirements' => ['view' => 'timeGridDay|timeGridWeek|dayGridMonth|multiMonthYear|listMonth'], 'postfix' => 'view.timerange.new'],
		['name' => 'view#index', 'url' => '/{view}/{timeRange}/edit/{mode}/{objectId}/{recurrenceId}', 'verb' => 'GET', 'requirements' => ['view' => 'timeGridDay|timeGridWeek|dayGridMonth|multiMonthYear|listMonth'], 'postfix' => 'view.timerange.edit'],
		// Appointments
		['name' => 'appointment#index', 'url' => '/appointments/{userId}', 'verb' => 'GET'],
		['name' => 'appointment#show', 'url' => '/appointment/{token}', 'verb' => 'GET'],
		['name' => 'booking#getBookableSlots', 'url' => '/appointment/{appointmentConfigToken}/slots', 'verb' => 'GET'],
		['name' => 'booking#bookSlot', 'url' => '/appointment/{appointmentConfigToken}/book', 'verb' => 'POST'],
		['name' => 'booking#showConfirmBooking', 'url' => '/appointment/confirm/{token}', 'verb' => 'GET'],
		['name' => 'booking#confirmBooking', 'url' => '/appointment/confirm/{token}', 'verb' => 'POST'],
		// Public views
		['name' => 'publicView#public_index_with_branding', 'url' => '/p/{token}', 'verb' => 'GET'],
		['name' => 'publicView#public_index_with_branding', 'url' => '/p/{token}/{view}/{timeRange}', 'verb' => 'GET', 'postfix' => 'publicview.timerange'],
		['name' => 'publicView#public_index_with_branding', 'url' => '/p/{token}/{view}/{timeRange}/view/{mode}/{objectId}/{recurrenceId}', 'verb' => 'GET', 'postfix' => 'publicview.timerange.view'],
		['name' => 'publicView#public_index_with_branding', 'url' => '/p/{token}/{fancyName}', 'verb' => 'GET', 'postfix' => 'fancy.name'],
		['name' => 'publicView#public_index_for_embedding', 'url' => '/embed/{token}', 'verb' => 'GET'],
		['name' => 'publicView#public_index_for_embedding', 'url' => '/embed/{token}/{view}/{timeRange}', 'verb' => 'GET', 'postfix' => 'publicview.timerange.embed'],
		['name' => 'publicView#public_index_for_embedding', 'url' => '/embed/{token}/{view}/{timeRange}/view/{mode}/{objectId}/{recurrenceId}', 'verb' => 'GET', 'postfix' => 'publicview.timerange.view.embed'],
		['name' => 'publicView#public_index_for_embedding', 'url' => '/public/{token}', 'verb' => 'GET', 'postfix' => 'legacy'],
		// Autocompletion
		['name' => 'contact#searchAttendee', 'url' => '/v1/autocompletion/attendee', 'verb' => 'POST'],
		['name' => 'contact#searchLocation', 'url' => '/v1/autocompletion/location', 'verb' => 'POST'],
		['name' => 'contact#searchPhoto', 'url' => '/v1/autocompletion/photo', 'verb' => 'POST'],
		// Circles
		['name' => 'contact#getCircleMembers', 'url' => '/v1/circles/getmembers', 'verb' => 'GET'],
		// Contact Groups
		['name' => 'contact#getContactGroupMembers', 'url' => '/v1/autocompletion/groupmembers', 'verb' => 'POST'],
		// Settings
		['name' => 'settings#setConfig', 'url' => '/v1/config/{key}', 'verb' => 'POST'],
		// Tools
		['name' => 'email#sendEmailPublicLink', 'url' => '/v1/public/sendmail', 'verb' => 'POST'],
		// NocTurn shift/rotation schedule (sync + CRUD)
		['name' => 'nocturnSchedule#sync', 'url' => '/v1/nocturn/sync', 'verb' => 'GET'],
		['name' => 'nocturnSchedule#upsertJob', 'url' => '/v1/nocturn/jobs', 'verb' => 'POST'],
		['name' => 'nocturnSchedule#deleteJob', 'url' => '/v1/nocturn/jobs/{uid}', 'verb' => 'DELETE'],
		['name' => 'nocturnSchedule#upsertShift', 'url' => '/v1/nocturn/shifts', 'verb' => 'POST'],
		['name' => 'nocturnSchedule#deleteShift', 'url' => '/v1/nocturn/shifts/{uid}', 'verb' => 'DELETE'],
		['name' => 'nocturnSchedule#upsertRotation', 'url' => '/v1/nocturn/rotations', 'verb' => 'POST'],
		['name' => 'nocturnSchedule#deleteRotation', 'url' => '/v1/nocturn/rotations/{uid}', 'verb' => 'DELETE'],
		['name' => 'nocturnSchedule#applyRotation', 'url' => '/v1/nocturn/rotations/{uid}/apply', 'verb' => 'POST'],
		['name' => 'nocturnSchedule#upsertOccurrence', 'url' => '/v1/nocturn/occurrences', 'verb' => 'POST'],
		['name' => 'nocturnSchedule#deleteOccurrence', 'url' => '/v1/nocturn/occurrences/{uid}', 'verb' => 'DELETE'],
		['name' => 'nocturnSchedule#ownOccurrences', 'url' => '/v1/nocturn/jobs/{jobUid}/occurrences', 'verb' => 'GET'],
		// NocTurn job sharing (share a job's full schedule with another Nextcloud user)
		['name' => 'nocturnSchedule#shareJob', 'url' => '/v1/nocturn/jobs/{uid}/share', 'verb' => 'POST'],
		['name' => 'nocturnSchedule#unshareJob', 'url' => '/v1/nocturn/jobs/{uid}/share/{sharedWithUserId}', 'verb' => 'DELETE'],
		['name' => 'nocturnSchedule#listJobShares', 'url' => '/v1/nocturn/jobs/{uid}/shares', 'verb' => 'GET'],
		['name' => 'nocturnSchedule#sharedWithMe', 'url' => '/v1/nocturn/shared-with-me', 'verb' => 'GET'],
		['name' => 'nocturnSchedule#sharedOccurrences', 'url' => '/v1/nocturn/shared/{ownerId}/jobs/{jobUid}/occurrences', 'verb' => 'GET'],
		['name' => 'nocturnSchedule#upsertSharedOccurrence', 'url' => '/v1/nocturn/shared/{ownerId}/jobs/{jobUid}/occurrences', 'verb' => 'POST'],
		['name' => 'nocturnSchedule#deleteSharedOccurrence', 'url' => '/v1/nocturn/shared/{ownerId}/jobs/{jobUid}/occurrences/{uid}', 'verb' => 'DELETE'],
	],
	'resources' => [
		'appointmentConfig' => ['url' => '/v1/appointment_configs']
	]
];
