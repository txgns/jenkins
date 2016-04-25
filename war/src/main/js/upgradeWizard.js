setupWizardExtension(function(wizard) {
	wizard.pluginTemplates.upgradeTo20Panel = require('./templates/upgradeTo20Panel.hbs');
	wizard.pluginTemplates.upgradeSuccessPanel = require('./templates/upgradeSuccessPanel.hbs');
	wizard.pluginTemplates.upgradeSkippedPanel = require('./templates/upgradeSkippedPanel.hbs');
	wizard.actions['.skip-recommended-plugins'] = function() {
		wizard.setPanel(wizard.pluginTemplates.upgradeSkippedPanel);
	};
	wizard.actions['.restart-upgrade-wizard'] = function() {
		wizard.setPanel(wizard.pluginTemplates.upgradeTo20Panel);
	};
	wizard.transitions['UPGRADE'] = function() {
		wizard.setPanel(wizard.pluginTemplates.upgradeSuccessPanel);
	};
});
