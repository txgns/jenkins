setupWizardExtension(function(wizard) {
	wizard.pluginTemplates.upgradeTo20Panel = require('./templates/upgradeTo20Panel.hbs');
	wizard.pluginTemplates.successPanel = require('./templates/successPanel.hbs');
	wizard.$wizard.on('click', '.skip-recommended-plugins', function() {
		wizard.setPanel(wizard.pluginTemplates.successPanel);
	});
});
