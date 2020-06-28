package ru.javaops.masterjava;

import com.google.common.io.Resources;
import j2html.tags.ContainerTag;
import one.util.streamex.StreamEx;
import ru.javaops.masterjava.xml.schema.ObjectFactory;
import ru.javaops.masterjava.xml.schema.Payload;
import ru.javaops.masterjava.xml.schema.Project;
import ru.javaops.masterjava.xml.schema.User;
import ru.javaops.masterjava.xml.util.JaxbParser;
import ru.javaops.masterjava.xml.util.Schemas;
import ru.javaops.masterjava.xml.util.StaxStreamProcessor;

import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public class MainXml {
    private static final Comparator<User> USER_COMPARATOR = Comparator.comparing(User::getValue)
            .thenComparing(User::getEmail);

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

    private static Set<User> parseByJaxb(String projectName, URL payloadUrl) throws Exception {
        JaxbParser parser = new JaxbParser(ObjectFactory.class);
        parser.setSchema(Schemas.ofClasspath("payload.xsd"));
        try (InputStream is = payloadUrl.openStream()) {
            Payload payload = parser.unmarshal(is);
            Project project = StreamEx.of(payload.getProjects().getProject())
                    .filter(p -> p.getName().equals(projectName))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid project name '" + projectName + "'"));
            Set<Project.Group> groups = new HashSet<>(project.getGroup());
            return StreamEx.of(payload.getUsers().getUser())
                    .filter(u -> StreamEx.of(u.getGroupRefs())
                            .findAny(groups::contains)
                            .isPresent()
                    ).collect(Collectors.toCollection(() -> new TreeSet<>(USER_COMPARATOR)));
        }
    }

    private static String outHtml(Set<User> users, String projectName, Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            final ContainerTag table = table().with(
                    tr().with(th("FullName"), th("email"))
            );
            users.forEach(u -> table.with(
                    tr().with(td(u.getValue()), td(u.getEmail()))
            ));

            table.attr("border", "1");
            table.attr("cellpadding", "8");
            table.attr("cellspacing", "0");

            String out = html().with(
                    head().with(title(projectName + " users")),
                    body().with(h1(projectName + " users"), table)
            ).render();
            writer.write(out);
            return out;
        }
    }

    public static void main(String[] args) throws Exception {
        Set<User> users = parseByJaxb("masterjava", Resources.getResource("payload.xml"));
        users.forEach(System.out::println);

        String html = outHtml(users, "masterjava", Paths.get("out/usersJaxb.html"));
        System.out.println(html);

//        System.out.println("\n:: StAX ::");
//        List<String> users2 = new StAXImpl().filterUsers("topjava");
//        users2.forEach(System.out::println);
    }
}
