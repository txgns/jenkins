setupWizardExtension(function(wizard) {
	var $ = wizard.$; // jQuery
	var jenkins = wizard.jenkins; // wizard-provided jenkins api
	var upgradeUrl = wizard.$wizard.parent().attr('data-upgrade-url');
	
	wizard.pluginTemplates.upgradeTo20Panel = handlebars.compile(fs.readFileSync(__dirname + '/./templates/upgradeTo20Panel.hbs', 'utf8'));
	wizard.pluginTemplates.upgradeSuccessPanel = handlebars.compile(fs.readFileSync(__dirname + '/./templates/upgradeSuccessPanel.hbs', 'utf8'));
	wizard.pluginTemplates.upgradeSkippedPanel = handlebars.compile(fs.readFileSync(__dirname + '/./templates/upgradeSkippedPanel.hbs', 'utf8'));
	wizard.actions['.skip-recommended-plugins'] = function() {
		wizard.setPanel(wizard.pluginTemplates.upgradeSkippedPanel);
	};
	wizard.actions['.restart-upgrade-wizard'] = function() {
		wizard.setPanel(wizard.pluginTemplates.upgradeTo20Panel);
	};
	wizard.actions['.perform-upgrade'] = function() {
		jenkins.post(upgradeUrl + '/upgrade', {}, function(response) {
			wizard.loadPluginData(function() {
				var pluginNames = response.data;
				wizard.setSelectedPluginNames(pluginNames);
				wizard.installPlugins(pluginNames);
			});
		},{});
	};
	wizard.actions['.close'] = function() {
		jenkins.goTo(upgradeUrl + '/hideUpgradeWizard');
	};
	wizard.actions['.install-home'] = function() {
		wizard.setPanel(wizard.pluginTemplates.upgradeTo20Panel);
	};
	$.extend(wizard.transitions, {
		UPGRADE: function() {
			wizard.setPanel(wizard.pluginTemplates.upgradeSuccessPanel);
		},
		CREATE_ADMIN_USER: function() {
			wizard.setPanel(wizard.pluginTemplates.upgradeSuccessPanel);
		},
		INITIAL_SETUP_COMPLETED: function() {
			wizard.setPanel(wizard.pluginTemplates.upgradeSuccessPanel);
		}
	});

	// overrides some install text
	wizard.translationOverride(function(translations) {
		translations.installWizard_installing_title = translations.installWizard_upgrading_title;
	});
});
