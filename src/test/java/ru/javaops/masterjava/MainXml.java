package ru.javaops.masterjava;

import com.google.common.io.Resources;
import ru.javaops.masterjava.xml.schema.*;
import ru.javaops.masterjava.xml.schema.Project.Group;
import ru.javaops.masterjava.xml.util.JaxbParser;
import ru.javaops.masterjava.xml.util.Schemas;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MainXml {
    private static final JaxbParser JAXB_PARSER = new JaxbParser(ObjectFactory.class);
    private static Payload payload = null;

    static {
        try {
            payload = JAXB_PARSER.unmarshal(Resources.getResource("payload.xml").openStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
        JAXB_PARSER.setSchema(Schemas.ofClasspath("payload.xsd"));
    }

    private static Set<String> findGroup(String projectName) {
        return payload.getProjects().getProject().stream()
                .filter(proj -> proj.getName().equalsIgnoreCase(projectName))
                .findFirst()
                .orElseThrow(IllegalStateException::new)
                .getGroup().stream()
                .map(Group::getName)
                .collect(Collectors.toSet());
    }

    private static List<User> filterUsers(Set<String> groups) {
        return payload.getUsers().getUser().stream()
                .filter(user -> {
                    List<Object> groupRefs = user.getGroupRefs();
                    return groupRefs.stream()
                            .map(group -> ((Group) group).getName())
                            .anyMatch(groups::contains);
                })
                .collect(Collectors.toList());
    }

    private static List<User> filterUsers(String projectName) {
        Set<String> groups = findGroup(projectName);
        return filterUsers(groups).stream()
                .sorted(Comparator.comparing(User::getValue))
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        List<User> users = filterUsers("masterjava");
        users.forEach(user -> System.out.println(user.getValue()));
    }
}
