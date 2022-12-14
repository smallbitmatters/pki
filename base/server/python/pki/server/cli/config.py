# Authors:
#     Endi S. Dewata <edewata@redhat.com>
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; version 2 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Copyright (C) 2018 Red Hat, Inc.
# All rights reserved.
#

from __future__ import absolute_import, print_function

import getopt
import logging
import sys

import pki.cli
import pki.server.instance

logger = logging.getLogger(__name__)


class SubsystemConfigCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'config', f'{parent.name.upper()} configuration management commands'
        )

        self.parent = parent
        self.add_module(SubsystemConfigFindCLI(self))
        self.add_module(SubsystemConfigShowCLI(self))
        self.add_module(SubsystemConfigSetCLI(self))
        self.add_module(SubsystemConfigUnsetCLI(self))


class SubsystemConfigFindCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'find', f'Find {parent.parent.name.upper()} configuration parameters'
        )
        self.parent = parent

    def print_help(self):
        print(f'Usage: pki-server {self.parent.parent.name}-config-find [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)
        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        subsystem_name = self.parent.parent.name
        subsystem = instance.get_subsystem(subsystem_name)

        if not subsystem:
            logger.error('No such subsystem: %s', subsystem_name.upper())
            sys.exit(1)

        for name, value in subsystem.config.items():
            print(f'{name}={value}')


class SubsystemConfigShowCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'show',
            f'Show {parent.parent.name.upper()} configuration parameter value',
        )
        self.parent = parent

    def print_help(self):
        print(
            f'Usage: pki-server {self.parent.parent.name}-config-show [OPTIONS] <name>'
        )
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) < 1:
            logger.error('Missing %s configuration parameter name',
                         self.parent.parent.name.upper())
            self.print_help()
            sys.exit(1)

        name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)
        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        subsystem_name = self.parent.parent.name
        subsystem = instance.get_subsystem(subsystem_name)

        if not subsystem:
            logger.error('No such subsystem: %s', subsystem_name.upper())
            sys.exit(1)

        if name in subsystem.config:
            value = subsystem.config[name]
            print(value)

        else:
            logger.error('No such parameter: %s', name)


class SubsystemConfigSetCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'set',
            f'Set {parent.parent.name.upper()} configuration parameter value',
        )
        self.parent = parent

    def print_help(self):
        print(
            f'Usage: pki-server {self.parent.parent.name}-config-set [OPTIONS] <name> <value>'
        )
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) < 1:
            logger.error('Missing %s configuration parameter name',
                         self.parent.parent.name.upper())
            self.print_help()
            sys.exit(1)

        if len(args) < 2:
            logger.error('Missing %s configuration parameter value',
                         self.parent.parent.name.upper())
            self.print_help()
            sys.exit(1)

        name = args[0]
        value = args[1]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)
        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        subsystem_name = self.parent.parent.name
        subsystem = instance.get_subsystem(subsystem_name)

        if not subsystem:
            logger.error('No such subsystem: %s', subsystem_name.upper())
            sys.exit(1)

        subsystem.config[name] = value
        subsystem.save()


class SubsystemConfigUnsetCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'unset', f'Unset {parent.parent.name.upper()} configuration parameter'
        )
        self.parent = parent

    def print_help(self):
        print(
            f'Usage: pki-server {self.parent.parent.name}-config-unset [OPTIONS] <name>'
        )
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) < 1:
            logger.error('Missing %s configuration parameter name',
                         self.parent.parent.name.upper())
            self.print_help()
            sys.exit(1)

        name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)
        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        subsystem_name = self.parent.parent.name
        subsystem = instance.get_subsystem(subsystem_name)

        if not subsystem:
            logger.error('No such subsystem: %s', subsystem_name.upper())
            sys.exit(1)

        subsystem.config.pop(name, None)
        subsystem.save()
