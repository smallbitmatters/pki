#
# Copyright Red Hat, Inc.
#
# SPDX-License-Identifier: GPL-2.0-or-later
#
from __future__ import absolute_import
from __future__ import print_function
import getopt
import logging
import sys

import pki.cli
import pki.server.instance

logger = logging.getLogger(__name__)


class GroupCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__('group', f'{parent.name.upper()} group management commands')

        self.parent = parent
        self.add_module(GroupFindCLI(self))
        self.add_module(GroupMemberCLI(self))


class GroupFindCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__('find', f'Find {parent.parent.name.upper()} groups')

        self.parent = parent

    def print_help(self):
        print(f'Usage: pki-server {self.parent.parent.name}-group-find [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):
        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        subsystem_name = self.parent.parent.name

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Invalid option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)
        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        subsystem = instance.get_subsystem(subsystem_name)

        if not subsystem:
            logger.error('No %s subsystem in instance %s',
                         subsystem_name.upper(), instance_name)
            sys.exit(1)

        subsystem.find_groups()


class GroupMemberCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'member', f'{parent.name.upper()} group member management commands'
        )

        self.parent = parent
        self.add_module(GroupMemberFindCLI(self))
        self.add_module(GroupMemberAddCLI(self))


class GroupMemberFindCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'find', f'Find {parent.parent.parent.name.upper()} group members'
        )

        self.parent = parent

    def print_help(self):
        print(
            f'Usage: pki-server {self.parent.parent.parent.name}-group-member-find [OPTIONS] <group ID>'
        )
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):
        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        subsystem_name = self.parent.parent.parent.name

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Invalid option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) < 1:
            logger.error('Missing group ID.')
            self.print_help()
            sys.exit(1)

        group_id = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)
        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        subsystem = instance.get_subsystem(subsystem_name)

        if not subsystem:
            logger.error('No %s subsystem in instance %s',
                         subsystem_name.upper(), instance_name)
            sys.exit(1)

        members = subsystem.find_group_members(group_id)

        first = True

        for member in members['entries']:
            if first:
                first = False
            else:
                print()

            print(f"  User ID: {member['id']}")


class GroupMemberAddCLI(pki.cli.CLI):

    def __init__(self, parent):
        super().__init__(
            'add', f'Add {parent.parent.parent.name.upper()} group member'
        )

        self.parent = parent

    def print_help(self):
        print(
            f'Usage: pki-server {self.parent.parent.parent.name}-group-member-add [OPTIONS] <group ID> <member ID>'
        )
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):
        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        subsystem_name = self.parent.parent.parent.name

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Invalid option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) < 1:
            logger.error('Missing group ID')
            self.print_help()
            sys.exit(1)

        if len(args) < 2:
            logger.error('Missing member ID')
            self.print_help()
            sys.exit(1)

        group_id = args[0]
        member_id = args[1]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)
        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        subsystem = instance.get_subsystem(subsystem_name)

        if not subsystem:
            logger.error('No %s subsystem in instance %s',
                         subsystem_name.upper(), instance_name)
            sys.exit(1)

        logger.info('Adding %s into %s', member_id, group_id)
        subsystem.add_group_member(group_id, member_id)
