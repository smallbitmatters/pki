import pki.server.upgrade


class BasicUpgradeScript(pki.server.upgrade.PKIServerUpgradeScriptlet):

    def __init__(self):
        super().__init__()
        self.message = 'Basic upgrade script'

    def upgrade_instance(self, instance):
        print(f'BasicUpgradeScript: Upgrading {instance.name} instance')

    def upgrade_subsystem(self, instance, subsystem):
        print(f'BasicUpgradeScript: Upgrading {subsystem.name} subsystem')
