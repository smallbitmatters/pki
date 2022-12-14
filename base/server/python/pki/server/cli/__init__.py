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
# Copyright (C) 2019 Red Hat, Inc.
# All rights reserved.
#

from __future__ import absolute_import
from __future__ import print_function
import getopt
import logging
import socket
import sys

import pki.cli
import pki.server
import pki.server.cli.acme
import pki.server.cli.audit
import pki.server.cli.banner
import pki.server.cli.ca
import pki.server.cli.cert
import pki.server.cli.config
import pki.server.cli.db
import pki.server.cli.est
import pki.server.cli.http
import pki.server.cli.instance
import pki.server.cli.jss
import pki.server.cli.kra
import pki.server.cli.listener
import pki.server.cli.migrate
import pki.server.cli.nss
import pki.server.cli.nuxwdog
import pki.server.cli.ocsp
import pki.server.cli.password
import pki.server.cli.sd
import pki.server.cli.selftest
import pki.server.cli.subsystem
import pki.server.cli.tks
import pki.server.cli.tps
import pki.server.cli.upgrade
import pki.server.cli.webapp
import pki.server.instance
import pki.util

logger = logging.getLogger(__name__)


class PKIServerCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('pki-server', 'PKI server command-line interface')

        self.add_module(pki.server.cli.CreateCLI())
        self.add_module(pki.server.cli.RemoveCLI())

        self.add_module(pki.server.cli.StatusCLI())
        self.add_module(pki.server.cli.StartCLI())
        self.add_module(pki.server.cli.StopCLI())
        self.add_module(pki.server.cli.RestartCLI())
        self.add_module(pki.server.cli.RunCLI())

        self.add_module(pki.server.cli.http.HTTPCLI())
        self.add_module(pki.server.cli.listener.ListenerCLI())

        self.add_module(pki.server.cli.password.PasswordCLI())
        self.add_module(pki.server.cli.nss.NSSCLI())
        self.add_module(pki.server.cli.jss.JSSCLI())

        self.add_module(pki.server.cli.webapp.WebappCLI())

        self.add_module(pki.server.cli.sd.SDCLI())
        self.add_module(pki.server.cli.ca.CACLI())
        self.add_module(pki.server.cli.kra.KRACLI())
        self.add_module(pki.server.cli.ocsp.OCSPCLI())
        self.add_module(pki.server.cli.tks.TKSCLI())
        self.add_module(pki.server.cli.tps.TPSCLI())
        self.add_module(pki.server.cli.acme.ACMECLI())
        self.add_module(pki.server.cli.est.ESTCLI())

        self.add_module(pki.server.cli.banner.BannerCLI())
        self.add_module(pki.server.cli.db.DBCLI())
        self.add_module(pki.server.cli.instance.InstanceCLI())
        self.add_module(pki.server.cli.subsystem.SubsystemCLI())
        self.add_module(pki.server.cli.migrate.MigrateCLI())
        self.add_module(pki.server.cli.nuxwdog.NuxwdogCLI())
        self.add_module(pki.server.cli.cert.CertCLI())
        self.add_module(pki.server.cli.selftest.SelfTestCLI())

        self.add_module(pki.server.cli.upgrade.UpgradeCLI())

    def get_full_module_name(self, module_name):
        return module_name

    def print_help(self):
        print('Usage: pki-server [OPTIONS]')
        print()
        print('  -v, --verbose                Run in verbose mode.')
        print('      --debug                  Show debug messages.')
        print('      --help                   Show help message.')
        print()

        super().print_help()

    def execute(self, argv):
        try:
            opts, args = getopt.getopt(argv[1:], 'v', [
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        for o, _ in opts:
            if o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option %s', o)
                self.print_help()
                sys.exit(1)

        logger.info('Command: %s', ' '.join(args))

        super().execute(args)

    @staticmethod
    def print_status(instance):
        print(f'  Instance ID: {instance.name}')
        print(f'  Active: {instance.is_active()}')
        print(f"  Nuxwdog Enabled: {instance.type.endswith('-nuxwdog')}")

        server_config = instance.get_server_config()

        unsecurePort = server_config.get_unsecure_port()
        if unsecurePort:
            print(f'  Unsecure Port: {unsecurePort}')

        securePort = server_config.get_secure_port()
        if securePort:
            print(f'  Secure Port: {securePort}')

        if ajpPort := server_config.get_ajp_port():
            print(f'  AJP Port: {ajpPort}')

        tomcatPort = server_config.get_port()
        print(f'  Tomcat Port: {tomcatPort}')

        hostname = socket.gethostname()

        if ca := instance.get_subsystem('ca'):
            print()
            print('  CA Subsystem:')

            if ca.config['subsystem.select'] == 'Clone':
                subsystem_type = 'CA Clone'
            else:
                subsystem_type = ca.config['hierarchy.select'] + ' CA'
            if ca.config['securitydomain.select'] == 'new':
                subsystem_type += ' (Security Domain)'
            print(f'    Type:                {subsystem_type}')

            print(f"    SD Name:             {ca.config['securitydomain.name']}")
            url = f"https://{ca.config['securitydomain.host']}:{ca.config['securitydomain.httpsadminport']}"
            print(f'    SD Registration URL: {url}')

            enabled = ca.is_enabled()
            print(f'    Enabled:             {enabled}')

            if enabled:
                url = f'http://{hostname}:{unsecurePort}/ca'
                print(f'    Unsecure URL:        {url}/ee/ca')

                url = f'https://{hostname}:{securePort}/ca'
                print(f'    Secure Agent URL:    {url}/agent/ca')
                print(f'    Secure EE URL:       {url}/ee/ca')
                print(f'    Secure Admin URL:    {url}/services')
                print(f'    PKI Console URL:     {url}')

        if kra := instance.get_subsystem('kra'):
            print()
            print('  KRA Subsystem:')

            subsystem_type = 'KRA'
            if kra.config['subsystem.select'] == 'Clone':
                subsystem_type += ' Clone'
            elif kra.config['kra.standalone'] == 'true':
                subsystem_type += ' (Standalone)'
            print(f'    Type:                {subsystem_type}')

            print(f"    SD Name:             {kra.config['securitydomain.name']}")
            url = f"https://{kra.config['securitydomain.host']}:{kra.config['securitydomain.httpsadminport']}"
            print(f'    SD Registration URL: {url}')

            enabled = kra.is_enabled()
            print(f'    Enabled:             {enabled}')

            if enabled:
                url = f'https://{hostname}:{securePort}/kra'
                print(f'    Secure Agent URL:    {url}/agent/kra')
                print(f'    Secure Admin URL:    {url}/services')
                print(f'    PKI Console URL:     {url}')

        if ocsp := instance.get_subsystem('ocsp'):
            print()
            print('  OCSP Subsystem:')

            subsystem_type = 'OCSP'
            if ocsp.config['subsystem.select'] == 'Clone':
                subsystem_type += ' Clone'
            elif ocsp.config['ocsp.standalone'] == 'true':
                subsystem_type += ' (Standalone)'
            print(f'    Type:                {subsystem_type}')

            print(f"    SD Name:             {ocsp.config['securitydomain.name']}")
            url = f"https://{ocsp.config['securitydomain.host']}:{ocsp.config['securitydomain.httpsadminport']}"
            print('    SD Registration URL: %s' % url)

            enabled = ocsp.is_enabled()
            print('    Enabled:             %s' % enabled)

            if enabled:
                url = 'http://%s:%s/ocsp' % (hostname, unsecurePort)
                print('    Unsecure URL:        %s/ee/ocsp/<ocsp request blob>' % url)

                url = 'https://%s:%s/ocsp' % (hostname, securePort)
                print('    Secure Agent URL:    %s/agent/ocsp' % url)
                print('    Secure EE URL:       %s/ee/ocsp/<ocsp request blob>' % url)
                print('    Secure Admin URL:    %s/services' % url)
                print('    PKI Console URL:     %s' % url)

        if tks := instance.get_subsystem('tks'):
            print()
            print('  TKS Subsystem:')

            subsystem_type = 'TKS'
            if tks.config['subsystem.select'] == 'Clone':
                subsystem_type += ' Clone'
            print('    Type:                %s' % subsystem_type)

            print('    SD Name:             %s' % tks.config['securitydomain.name'])
            url = 'https://%s:%s' % (
                tks.config['securitydomain.host'],
                tks.config['securitydomain.httpsadminport'])
            print('    SD Registration URL: %s' % url)

            enabled = tks.is_enabled()
            print('    Enabled:             %s' % enabled)

            if enabled:
                url = 'https://%s:%s/tks' % (hostname, securePort)
                print('    Secure Agent URL:    %s/agent/tks' % url)
                print('    Secure Admin URL:    %s/services' % url)
                print('    PKI Console URL:     %s' % url)

        if tps := instance.get_subsystem('tps'):
            print()
            print('  TPS Subsystem:')

            subsystem_type = 'TPS'
            if tps.config['subsystem.select'] == 'Clone':
                subsystem_type += ' Clone'
            print('    Type:                %s' % subsystem_type)

            print('    SD Name:             %s' % tps.config['securitydomain.name'])
            url = 'https://%s:%s' % (
                tps.config['securitydomain.host'],
                tps.config['securitydomain.httpsadminport'])
            print('    SD Registration URL: %s' % url)

            enabled = tps.is_enabled()
            print('    Enabled:             %s' % enabled)

            if enabled:
                url = 'http://%s:%s/tps' % (hostname, unsecurePort)
                print('    Unsecure URL:        %s' % url)
                print('    Unsecure PHONE HOME: %s/phoneHome' % url)

                url = 'https://%s:%s/tps' % (hostname, securePort)
                print('    Secure URL:          %s' % url)
                print('    Secure PHONE HOME:   %s/phoneHome' % url)


class CreateCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('create', 'Create PKI server')

    def print_help(self):
        print('Usage: pki-server create [OPTIONS] [<instance ID>]')
        print()
        print('      --user <name>             User.')
        print('      --group <name>            Group.')
        print('      --with-maven-deps         Install Maven dependencies.')
        print('      --force                   Force creation.')
        print('  -v, --verbose                 Run in verbose mode.')
        print('      --debug                   Run in debug mode.')
        print('      --help                    Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'v', [
                'user=', 'group=',
                'with-maven-deps', 'force',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        user = None
        group = None
        with_maven_deps = False
        force = False

        for o, a in opts:
            if o == '--user':
                user = a

            elif o == '--group':
                group = a

            elif o == '--with-maven-deps':
                with_maven_deps = True

            elif o == '--force':
                force = True

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance_name = args[0] if len(args) > 0 else 'pki-tomcat'
        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not force and instance.exists():
            logger.error('Instance already exists: %s', instance_name)
            sys.exit(1)

        logger.info('Creating instance: %s', instance_name)

        if user:
            instance.user = user

        if group:
            instance.group = group

        instance.with_maven_deps = with_maven_deps

        instance.create(force=force)


class RemoveCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('remove', 'Remove PKI server')

    def print_help(self):
        print('Usage: pki-server remove [OPTIONS] [<instance ID>]')
        print()
        print('      --force                   Force removal.')
        print('  -v, --verbose                 Run in verbose mode.')
        print('      --debug                   Run in debug mode.')
        print('      --help                    Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'v', [
                'force',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        force = False

        for o, _ in opts:
            if o == '--force':
                force = True

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance_name = args[0] if len(args) > 0 else 'pki-tomcat'
        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not force and not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        logger.info('Removing instance: %s', instance_name)

        instance.remove(force=force)


class StatusCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('status', 'Display PKI service status')

    def print_help(self):
        print('Usage: pki-server status [OPTIONS] [<instance ID>]')
        print()
        print('  -v, --verbose                 Run in verbose mode.')
        print('      --debug                   Run in debug mode.')
        print('      --help                    Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'v', [
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        for o, _ in opts:
            if o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance_name = args[0] if len(args) > 0 else 'pki-tomcat'
        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        PKIServerCLI.print_status(instance)


class StartCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('start', 'Start PKI service')

    def print_help(self):
        print('Usage: pki-server start [OPTIONS] [<instance ID>]')
        print()
        print('      --wait                    Wait until started.')
        print('      --max-wait <seconds>      Maximum wait time (default: 60)')
        print('      --timeout <seconds>       Connection timeout')
        print('  -v, --verbose                 Run in verbose mode.')
        print('      --debug                   Run in debug mode.')
        print('      --help                    Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'v', [
                'wait', 'max-wait=', 'timeout=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        wait = False
        max_wait = 60
        timeout = None

        for o, a in opts:
            if o == '--wait':
                wait = True

            elif o == '--max-wait':
                max_wait = int(a)

            elif o == '--timeout':
                timeout = int(a)

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            instance_name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        if instance.is_active():
            self.print_message('Instance already started')
            return

        instance.start(wait=wait, max_wait=max_wait, timeout=timeout)


class StopCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('stop', 'Stop PKI service')

    def print_help(self):
        print('Usage: pki-server stop [OPTIONS] [<instance ID>]')
        print()
        print('      --wait                    Wait until stopped.')
        print('      --max-wait <seconds>      Maximum wait time (default: 60)')
        print('      --timeout <seconds>       Connection timeout')
        print('  -v, --verbose                 Run in verbose mode.')
        print('      --debug                   Run in debug mode.')
        print('      --help                    Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'v', [
                'wait', 'max-wait=', 'timeout=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        wait = False
        max_wait = 60
        timeout = None

        for o, a in opts:
            if o == '--wait':
                wait = True

            elif o == '--max-wait':
                max_wait = int(a)

            elif o == '--timeout':
                timeout = int(a)

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            instance_name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        if not instance.is_active():
            self.print_message('Instance already stopped')
            return

        instance.stop(wait=wait, max_wait=max_wait, timeout=timeout)


class RestartCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('restart', 'Restart PKI service')

    def print_help(self):
        print('Usage: pki-server restart [OPTIONS] [<instance ID>]')
        print()
        print('      --wait                    Wait until restarted.')
        print('      --max-wait <seconds>      Maximum wait time (default: 60)')
        print('      --timeout <seconds>       Connection timeout')
        print('  -v, --verbose                 Run in verbose mode.')
        print('      --debug                   Run in debug mode.')
        print('      --help                    Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'v', [
                'wait', 'max-wait=', 'timeout=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        wait = False
        max_wait = 60
        timeout = None

        for o, a in opts:
            if o == '--wait':
                wait = True

            elif o == '--max-wait':
                max_wait = int(a)

            elif o == '--timeout':
                timeout = int(a)

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            instance_name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.restart(wait=wait, max_wait=max_wait, timeout=timeout)


class RunCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('run', 'Run PKI server in foreground')

    def print_help(self):
        print('Usage: pki-server run [OPTIONS] [<instance ID>]')
        print()
        print('      --as-current-user         Run as current user.')
        print('      --with-jdb                Run with Java debugger.')
        print('      --with-gdb                Run with GNU debugger.')
        print('      --with-valgrind           Run with Valgrind.')
        print('      --agentpath <value>       Java agent path.')
        print('  -v, --verbose                 Run in verbose mode.')
        print('      --debug                   Run in debug mode.')
        print('      --help                    Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'v', [
                'as-current-user',
                'with-jdb', 'with-gdb', 'with-valgrind',
                'agentpath=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        as_current_user = False
        with_jdb = False
        with_gdb = False
        with_valgrind = False
        agentpath = None

        for o, a in opts:
            if o == '--as-current-user':
                as_current_user = True

            elif o == '--with-jdb':
                with_jdb = True

            elif o == '--with-gdb':
                with_gdb = True

            elif o == '--with-valgrind':
                with_valgrind = True

            elif o == '--agentpath':
                agentpath = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            instance_name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            logger.error('Invalid instance: %s', instance_name)
            sys.exit(1)

        instance.load()

        try:
            instance.run(
                as_current_user=as_current_user,
                with_jdb=with_jdb,
                with_gdb=with_gdb,
                with_valgrind=with_valgrind,
                agentpath=agentpath)

        except KeyboardInterrupt:
            logger.debug('Server stopped')
