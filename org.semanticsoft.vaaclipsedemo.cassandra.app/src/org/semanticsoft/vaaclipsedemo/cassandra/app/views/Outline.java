package org.semanticsoft.vaaclipsedemo.cassandra.app.views;

import com.vaadin.data.Item;
import com.vaadin.terminal.Resource;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Tree;
import com.vaadin.ui.VerticalLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.advanced.MArea;
import org.eclipse.e4.ui.model.application.ui.basic.MInputPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.semanticsoft.vaaclipsedemo.cassandra.app.editors.FileUtils;

public class Outline
{
	MArea editorArea;
	Pattern packagePattern = Pattern.compile("package +([a-zA-Z0-9\\.\\_]+) *;");
	protected Pattern importsPattern = Pattern.compile("import +([a-zA-Z0-9\\.\\_]+) *;");
	public Pattern methodsPattern = Pattern.compile("(private|public|protected) +([a-zA-Z0-9\\_]+) +([a-zA-Z0-9\\_]+) *\\(.*?\\)");
	
	private Tree tree;
	private Panel panel;
	private Item packageItem;
	private Item importsItem;
	private Item classItem;
	private IEventBroker eventBroker;
	
	private static final String ICON_PROP = "icon";
	private static final String CAPTION_PROP = "caption";
	
	
	@Inject
	public Outline(VerticalLayout parent, IEclipseContext context, EModelService modelService, MApplication app)
	{
		panel = new Panel();
		panel.setSizeFull();
		tree = new Tree();
		tree.setImmediate(true);
		panel.addComponent(tree);
		
		parent.addComponent(panel);
		
		tree.addContainerProperty(ICON_PROP, Resource.class, null);
		tree.addContainerProperty(CAPTION_PROP, String.class, "NoName");
		
		tree.setItemCaptionPropertyId(CAPTION_PROP);
		tree.setItemIconPropertyId(ICON_PROP);
		
		editorArea = (MArea) modelService.find("org.semanticsoft.vaaclipsedemo.cassandra.app.editorarea", app);
		eventBroker = context.get(IEventBroker.class);
	}
	
	@PostConstruct
	void registerHandler()
	{
		eventBroker.subscribe(UIEvents.ElementContainer.TOPIC_SELECTEDELEMENT, selectElementHandler);
	}
	
	@PreDestroy
	void unregisterHandlers()
	{
		eventBroker.unsubscribe(selectElementHandler);
	}
	
	private EventHandler selectElementHandler = new EventHandler() {
		public void handleEvent(Event event) {
			Object element = event.getProperty(UIEvents.EventTags.ELEMENT);

			if (!(element instanceof MPartStack))
				return;
			
			MPartStack stack = (MPartStack) element;
			if (stack.getSelectedElement() instanceof MInputPart)
			{
				MInputPart inputPart = (MInputPart)stack.getSelectedElement();
				File file = new File(inputPart.getInputURI());
				String content;
				try
				{
					content = FileUtils.readFile(file, "UTF-8");
					refreshTree(content);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}	
			}
		}
	};

	public Object getPlatformComponent()
	{	
		return panel;
	}
	
	private void refreshTree(String content)
	{
		tree.removeAllItems();
		
		String pkg = parsePackage(content);
		List<String> imports = parseImports(content);
		List<Method> methodList = parseMethods(content);
		
		packageItem = tree.addItem("package");
		tree.setChildrenAllowed("package", false);
		packageItem.getItemProperty(ICON_PROP).setValue(new ThemeResource("img/package_declaration.png"));
		packageItem.getItemProperty(CAPTION_PROP).setValue(pkg);
		
		importsItem = tree.addItem("import_declarations");
		importsItem.getItemProperty(CAPTION_PROP).setValue("import declarations");
		
		classItem = tree.addItem("class");
		classItem.getItemProperty(ICON_PROP).setValue(new ThemeResource("img/class_declaration.png"));
		classItem.getItemProperty(CAPTION_PROP).setValue("Class");
		
		for (String imp : imports)
		{
			Item item = tree.addItem(imp);
			tree.setChildrenAllowed(imp, false);
			item.getItemProperty("caption").setValue(imp);
			item.getItemProperty("icon").setValue(new ThemeResource("img/package_declaration.png"));
			tree.setParent(imp, "import_declarations");
		}
		
		for (Method m : methodList)
		{
			String type = "pkg_method.png";
			if (m.modifier == Method.Modifier._public)
				type = "public_method.png";
			else if (m.modifier == Method.Modifier._private)
				type = "private_method.png";
			else if (m.modifier == Method.Modifier._protected)
				type = "protected_method.png";
			
			Item item = tree.addItem(m.name);
			tree.setChildrenAllowed(m.name, false);
			item.getItemProperty("caption").setValue(m.name);
			item.getItemProperty("icon").setValue(new ThemeResource("img/" + type));
			tree.setParent(m.name, "class");
		}
		
//		for (Object id : tree.rootItemIds())
//		{
//			tree.expandItemsRecursively(id);
//		}
		
		tree.expandItemsRecursively("class");
	}

	private static class Method
	{
		public enum Modifier
		{
			_public, _private, _protected
		}

		int lineNum;
		String type;
		String name;
		Modifier modifier;
	}
	
	private String parsePackage(String file)
	{
		Matcher m = packagePattern.matcher(file);
		if (m.matches())
		{
			return m.group(1);
		}
		else
			return "";
	}
	
	private List<String> parseImports(String file)
	{
		Matcher m = importsPattern.matcher(file);
		
		List<String> list = new ArrayList<String>();
		while (m.find())
		{
			list.add(m.group(1));
		}
		return list;
	}

	private List<Method> parseMethods(String file)
	{
		// the toy demo parsing - search methods by their modifiers (so methods
		// without modifiers are not finded)
		Matcher m = methodsPattern.matcher(file);
		
		List<Method> list = new ArrayList<Outline.Method>();
		while (m.find())
		{
			Method method = new Method();
			String mod = m.group(1);
			if (mod.equals("private"))
				method.modifier = Method.Modifier._private;
			else if (mod.equals("protected"))
				method.modifier = Method.Modifier._protected;
			else if (mod.equals("public"))
				method.modifier = Method.Modifier._public;
			
			method.type = m.group(2);
			method.name = m.group(3);
			
			list.add(method);
		}
		return list;
	}

	public static void main(String[] args)
	{
		Pattern pattern = Pattern.compile("package +([a-zA-Z0-9\\._]+) *;");
		Matcher m = pattern
				.matcher(
"/** * */package com.example.vaadin_development2.component_factories;");
		while (m.find())
		{
			String mod = m.group(1);
			System.out.println(String.format("%s", mod));
		}
	}
}
