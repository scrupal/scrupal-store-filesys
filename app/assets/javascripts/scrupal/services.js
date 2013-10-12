/*
 * This file is part of Scrupal a Web Application Framework.
 *
 * Copyright (c) 2013, Reid Spencer and viritude llc. All Rights Reserved.
 *
 * Scrupal is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * Scrupal is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with Scrupal. If not, see either:
 * http://www.gnu.org/licenses or http://opensource.org/licenses/GPL-3.0.
 */

'use strict';

/** Scrupal Services Definitions */

define(['angular'], function(angular) {
    // Demonstrate how to register services
    // In this case it is a simple value service.
    var mod = angular.module('scrupal.services', [])

    // Simple value service for the version of the Scrupal UI
    mod.value('version', '0.1');

    return mod;

})
