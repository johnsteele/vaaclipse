/**
 * 
 */
package org.semanticsoft.vaaclipse.theme;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.RegistryFactory;
import org.semanticsoft.vaaclipse.publicapi.theme.Theme;
import org.semanticsoft.vaaclipse.publicapi.theme.ThemeContribution;
import org.semanticsoft.vaaclipse.publicapi.theme.ThemeEngine;

/**
 * @author rushan
 *
 */
public class ThemeEngineImpl implements ThemeEngine
{
	private Map<String, ThemeImpl> themes = new HashMap<String, ThemeImpl>();
	private Map<String, ThemeImpl> themesByWebId = new HashMap<String, ThemeImpl>();
	private Map<String, ThemeContributionImpl> contributionsByWebId = new HashMap<String, ThemeContributionImpl>();
	
	public void activate()
	{
		IExtensionRegistry registry = RegistryFactory.getRegistry();
		IExtensionPoint extPoint = registry.getExtensionPoint("org.semanticsoft.vaaclipse.theme");

		//load any modified style sheets
		File modDir= new File(
				System.getProperty("user.home") + System.getProperty("file.separator") + ".e4css" + System.getProperty("file.separator")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		File[] modifiedFiles = modDir.listFiles();
		
		Map<Theme, List<String>> inherited = new HashMap<>();
		//add themes
		for (IExtension e : extPoint.getExtensions()) {
			for (IConfigurationElement ce : e.getConfigurationElements()) {
				if (ce.getName().equals("theme")) 
				{
					String themeId = ce.getAttribute("id");
					if (!themes.containsKey(themeId))
					{
						String label = ce.getAttribute("label");
						String description = ce.getAttribute("description");
						
						ThemeImpl theme = new ThemeImpl(themeId, label);
						setupThemeEntry(theme, ce);
						
						List<String> inheritedList = new ArrayList<>();
						for (IConfigurationElement inheritedThemeEl : ce.getChildren("inheritedTheme")) {
							String refid = inheritedThemeEl.getAttribute("refid");
							if (refid != null) {
								inheritedList.add(refid);
							}
						}
						
						inherited.put(theme, inheritedList);
						
						theme.setDescription(description);
						themes.put(themeId, theme);
						themesByWebId.put(theme.getWebId(), theme);
					}
				}
			}
		}
		
		//add inherited themes
		for (ThemeImpl theme : themes.values())
		{
			List<String> inheritedThemes = inherited.get(theme);
			for (String inheritedThemeId : inheritedThemes)
			{
				ThemeImpl inheritedTheme = themes.get(inheritedThemeId);
				if (inheritedTheme != null)
					theme.addInheritedTheme(inheritedTheme);
			}
		}
		
		//add user contributions
		for (IExtension e : extPoint.getExtensions()) {
			for (IConfigurationElement ce : e.getConfigurationElements()) {
				if (ce.getName().equals("themecontribution")) {
					IConfigurationElement[] cces = ce.getChildren("themeid");
					if (cces.length > 0) {
						String contributionId = ce.getAttribute("id");
						ThemeContributionImpl contribution = new ThemeContributionImpl(contributionId);
						setupThemeEntry(contribution, ce);
						
						String insertPosition = ce.getAttribute("insertPosition");
						if (insertPosition != null)
							contribution.setInsertPosition(insertPosition);
						
						for (int i = 0; i < cces.length; i++) {
							String themeId = cces[i].getAttribute("refid");
							if (themeId != null && themeId.length() > 0)
							{
								ThemeImpl theme = themes.get(themeId);
								if (theme != null)
								{
									theme.addContribution(contribution);
								}	
							}
						}
						
						contributionsByWebId.put(contribution.getWebId(), contribution);
					}
				}
			}
		}
		
		for (ThemeImpl theme : themes.values())
		{
			try
			{
				theme.prepareTheme();
			}
			catch (Exception e1)
			{
				e1.printStackTrace();
			}
		}
	}

	private void setupThemeEntry(ThemeEntryImpl themeEntry, IConfigurationElement ce)
	{
		String cssUri = ce.getAttribute("cssuri");
		if (!cssUri.startsWith("platform:/plugin/"))
		{
			cssUri = "platform:/plugin/" + ce.getContributor().getName() + "/" + cssUri;	
		}
		
		themeEntry.setCssUri(cssUri);
		
		for (IConfigurationElement resourceEl : ce.getChildren("resourceuri")) {
			String resourceUri = resourceEl.getAttribute("uri");
			if (resourceUri != null) {
				themeEntry.addResourceUri(resourceUri);
			}
		}
	}
	
	@Override
	public Theme getTheme(String themeId)
	{
		return themes.get(themeId);
	}
	
	@Override
	public Theme getThemeByWebId(String themeWebId)
	{
		return themesByWebId.get(themeWebId);
	}

	@Override
	public ThemeContribution getThemeContributionByWebId(String contributionWebId)
	{
		return contributionsByWebId.get(contributionWebId);
	}
}