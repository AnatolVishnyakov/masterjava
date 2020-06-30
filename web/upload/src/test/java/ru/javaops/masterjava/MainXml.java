package ru.javaops.masterjava;

import com.google.common.base.Splitter;
import com.google.common.io.Resources;
import j2html.tags.ContainerTag;
import one.util.streamex.StreamEx;
import ru.javaops.masterjava.xml.schema.ObjectFactory;
import ru.javaops.masterjava.xml.schema.Payload;
import ru.javaops.masterjava.xml.schema.Project;
import ru.javaops.masterjava.xml.schema.User;
import ru.javaops.masterjava.xml.util.*;

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

import static com.google.common.base.Strings.nullToEmpty;
import static j2html.TagCreator.*;

public class MainXml {
    private static final Comparator<User> USER_COMPARATOR = Comparator.comparing(User::getValue)
            .thenComparing(User::getEmail);
    private static final String PROJECT = "Project";
    private static final String GROUP = "Group";

    private static Set<User> parseByJaxb(String projectName, URL payloadUrl) throws Exception {
        JaxbParser parser = new JaxbParser(ObjectFactory.class);
        JaxbUnmarshaller unmarshaller = parser.createUnmarshaller();
        parser.setSchema(Schemas.ofClasspath("payload.xsd"));
        try (InputStream is = payloadUrl.openStream()) {
            Payload payload = unmarshaller.unmarshal(is);
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

    private static Set<User> processByStax(String projectName, URL payloadUrl) throws Exception {
        try (InputStream is = payloadUrl.openStream()) {
            StaxStreamProcessor processor = new StaxStreamProcessor(is);
            Set<String> groupNames = new HashSet<>();

            while (processor.startElement(PROJECT, "Projects")) {
                if (projectName.equals(processor.getAttribute("name"))) {
                    while (processor.startElement(GROUP, PROJECT)) {
                        groupNames.add(processor.getAttribute("name"));
                    }
                    break;
                }
            }

            if (groupNames.isEmpty()) {
                throw new IllegalArgumentException("Invalid " + projectName + " or no groups");
            }

            // Users loop
            Set<User> users = new TreeSet<>(USER_COMPARATOR);

            JaxbParser parser = new JaxbParser(ObjectFactory.class);
            JaxbUnmarshaller unmarshaller = parser.createUnmarshaller();
            while (processor.doUntil(XMLEvent.START_ELEMENT, "User")) {
                String groupRefs = processor.getAttribute("groupRefs");
                if (!Collections.disjoint(groupNames, Splitter.on(' ').splitToList(nullToEmpty(groupRefs)))) {
                    User user = unmarshaller.unmarshal(processor.getReader(), User.class);
                    users.add(user);
                }
            }
            return users;
        }
    }

    private static String transform(String projectName, URL payloadUrl) throws Exception {
        URL xsl = Resources.getResource("groups.xsl");
        try (InputStream xmlStream = payloadUrl.openStream(); InputStream xslStream = xsl.openStream()) {
            XsltProcessor processor = new XsltProcessor(xslStream);
            processor.setParameter("projectName", projectName);
            return processor.transform(xmlStream);
        }
    }

    public static void main(String[] args) throws Exception {
        String projectName = "topjava";
        URL payloadUrl = Resources.getResource("payload.xml");

        Set<User> users = parseByJaxb(projectName, payloadUrl);
        users.forEach(System.out::println);

        String html = outHtml(users, projectName, Paths.get("out/usersJaxb.html"));
        System.out.println(html);

        users = processByStax(projectName, payloadUrl);
        users.forEach(System.out::println);

        System.out.println();
        html = transform(projectName, payloadUrl);
        try (Writer writer = Files.newBufferedWriter(Paths.get("out/groups.html"))) {
            writer.write(html);
        }
        System.out.println(html);
    }
}
