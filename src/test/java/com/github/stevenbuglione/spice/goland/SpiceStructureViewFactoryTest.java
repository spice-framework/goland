package com.github.stevenbuglione.spice.goland;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Arrays;
import java.util.List;

public final class SpiceStructureViewFactoryTest
        extends BasePlatformTestCase {
    public void testGroupsConstructorStaticFactoriesAndMethodsUnderType() {
        myFixture.configureByText(
                "go.mod",
                "module example.com/application\n\ngo 1.26.0\n"
        );
        myFixture.configureByText(
                "order_service.go",
                """
                        package application

                        type OrderService struct {
                            repository string
                        }

                        func NewOrderService(repository string) *OrderService {
                            return &OrderService{repository: repository}
                        }

                        func ParseOrderService(value string) (*OrderService, error) {
                            return NewOrderService(value), nil
                        }

                        func OrderServiceFromString(value string) *OrderService {
                            return NewOrderService(value)
                        }

                        func (service *OrderService) Create() error { return nil }
                        func Utility() {}
                        """
        );

        SpiceStructureViewFactory factory =
                new SpiceStructureViewFactory();
        TreeBasedStructureViewBuilder builder =
                (TreeBasedStructureViewBuilder)
                        factory.getStructureViewBuilder(myFixture.getFile());
        assertNotNull(builder);
        StructureViewModel model = builder.createStructureViewModel(
                myFixture.getEditor()
        );
        List<TreeElement> root = Arrays.asList(
                model.getRoot().getChildren()
        );
        assertEquals(
                root.stream().map(this::text).toList().toString(),
                1,
                root.stream().filter(value -> text(value)
                        .contains("OrderService")).count()
        );
        assertTrue(
                root.stream().map(this::text).toList().toString(),
                root.stream().anyMatch(value -> text(value)
                        .startsWith("Utility"))
        );
        assertFalse(
                root.stream().map(this::text).toList().toString(),
                root.stream().anyMatch(value -> text(value)
                        .startsWith("NewOrderService"))
        );

        TreeElement type = root.stream()
                .filter(value -> text(value).contains("OrderService"))
                .findFirst()
                .orElseThrow();
        List<String> members = Arrays.stream(type.getChildren())
                .map(this::text)
                .toList();
        assertTrue(members.toString(), members.stream().anyMatch(value ->
                value.startsWith("constructor NewOrderService")));
        assertTrue(members.toString(), members.stream().anyMatch(value ->
                value.startsWith("static ParseOrderService")));
        assertTrue(members.toString(), members.stream().anyMatch(value ->
                value.startsWith("static OrderServiceFromString")));
        assertTrue(members.toString(), members.stream().anyMatch(value ->
                value.startsWith("Create")));
        assertTrue(members.toString(), indexOf(members, "constructor ")
                < indexOf(members, "static ParseOrderService"));
        assertTrue(members.toString(), indexOf(members,
                "static ParseOrderService") < indexOf(members,
                "static OrderServiceFromString"));
        assertTrue(members.toString(), indexOf(members, "repository")
                < indexOf(members, "Create"));
    }

    private int indexOf(List<String> values, String prefix) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).startsWith(prefix)) {
                return index;
            }
        }
        fail("missing " + prefix + " in " + values);
        return -1;
    }

    private String text(TreeElement value) {
        String result = value.getPresentation().getPresentableText();
        return result == null ? "" : result;
    }
}
