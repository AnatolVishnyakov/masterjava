package ru.javaops.masterjava;

import com.google.common.io.Resources;
import j2html.tags.ContainerTag;
import ru.javaops.masterjava.xml.schema.ObjectFactory;
import ru.javaops.masterjava.xml.schema.Payload;
import ru.javaops.masterjava.xml.schema.Project.Group;
import ru.javaops.masterjava.xml.schema.User;
import ru.javaops.masterjava.xml.util.JaxbParser;
import ru.javaops.masterjava.xml.util.Schemas;
import ru.javaops.masterjava.xml.util.StaxStreamProcessor;

import javax.xml.stream.events.XMLEvent;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public class MainXml {
    public static class JaxbImpl {
        private final JaxbParser JAXB_PARSER = new JaxbParser(ObjectFactory.class);
        private Payload payload = null;

        {
            try {
                payload = JAXB_PARSER.unmarshal(Resources.getResource("payload.xml").openStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
            JAXB_PARSER.setSchema(Schemas.ofClasspath("payload.xsd"));
        }

        private Set<String> findGroup(String projectName) {
            return payload.getProjects().getProject().stream()
                    .filter(proj -> proj.getName().equalsIgnoreCase(projectName))
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new)
                    .getGroup().stream()
                    .map(Group::getName)
                    .collect(Collectors.toSet());
        }

        private List<User> filterUsers(Set<String> groups) {
            return payload.getUsers().getUser().stream()
                    .filter(user -> {
                        List<Object> groupRefs = user.getGroupRefs();
                        return groupRefs.stream()
                                .map(group -> ((Group) group).getName())
                                .anyMatch(groups::contains);
                    })
                    .collect(Collectors.toList());
        }

        private Set<User> filterUsers(String projectName) {
            Set<String> groups = findGroup(projectName);
            return filterUsers(groups).stream()
                    .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(User::getValue).thenComparing(User::getEmail))));
        }

        private static String toHtml(Set<User> users, String projectName) {
            final ContainerTag table = table().with(
                    tr().with(th("FullName"), th("email")))
                    .attr("border", "1")
                    .attr("cellpadding", "8")
                    .attr("cellspacing", "0");

            users.forEach(u -> table.with(tr().with(td(u.getValue()), td(u.getEmail()))));

            return html().with(
                    head().with(title(projectName + " users")),
                    body().with(h1(projectName + " users"), table)
            ).render();
        }
    }

    public static class StAXImpl {
        private Set<String> findGroup(String projectName) {
            Set<String> groups = new HashSet<>(3);
            URL resource = Resources.getResource("payload.xml");
            try (StaxStreamProcessor processor = new StaxStreamProcessor(resource.openStream())) {
                while (processor.doUntil(XMLEvent.START_ELEMENT, "Project")) {
                    String attribute = processor.getAttribute("name");
                    if (attribute != null && attribute.equalsIgnoreCase(projectName)) {
                        while (processor.startElement("Group", "Project")) {
                            attribute = processor.getAttribute("name");
                            groups.add(attribute);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return groups;
        }

        public List<String> filterUsers(String projectName) throws Exception {
            Set<String> groups = findGroup(projectName);
            List<String> users = new ArrayList<>();
            URL resource = Resources.getResource("payload.xml");
            try (StaxStreamProcessor processor = new StaxStreamProcessor(resource.openStream())) {
                while (processor.startElement("User", "Users")) {
                    String[] attributes = Optional.ofNullable(processor.getAttribute("groupRefs"))
                            .orElse("")
                            .split(" ");
                    if (Arrays.stream(attributes).anyMatch(groups::contains)) {
                        String email = processor.getAttribute("email");
                        String fullName = processor.getText();
                        users.add(fullName + "/" + email);
                    }
                }
            }
            users.sort(String::compareTo);
            return users;
        }
    }

    public static void main(String[] args) throws Exception {
        JaxbImpl jaxb = new JaxbImpl();
        Set<User> users1 = jaxb.filterUsers("topjava");
        System.out.println(":: JAXB ::");
        System.out.println(jaxb.toHtml(users1, "topjava"));

        System.out.println("\n:: StAX ::");
        List<String> users2 = new StAXImpl().filterUsers("topjava");
        users2.forEach(System.out::println);
    }
}
