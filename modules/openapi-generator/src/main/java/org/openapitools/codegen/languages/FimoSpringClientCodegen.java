package org.openapitools.codegen.languages;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.tags.Tag;
import org.openapitools.codegen.*;

import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static org.openapitools.codegen.utils.StringUtils.camelize;

public class FimoSpringClientCodegen extends DefaultCodegen implements CodegenConfig {

    public static final String PROJECT_NAME = "projectName";

    private final Logger LOGGER = LoggerFactory.getLogger(FimoSpringClientCodegen.class);

    private static final String DEFAULT_GROUP_ID= "com.fimohealth";
    private static final String DEFAULT_ARTIFACT_ID = "example-client";
    private static final String DEFAULT_ARTIFACT_VERSION = "0.0.1-SNAPSHOT";

    private static final String PROJECT_FOLDER = "src/main";
    private static final String SOURCE_FOLDER = PROJECT_FOLDER+"/java";

    public FimoSpringClientCodegen() {
        super();

        outputFolder = "generated-code" + File.separator + "fimo-spring";
        modelTemplateFiles.put("model.mustache", ".java");
        apiTemplateFiles.put("api.mustache", ".java");
        embeddedTemplateDir = templateDir = "fimo-spring";
        apiPackage = "api";
        modelPackage = "model";

        cliOptions.add(new CliOption(CodegenConstants.GROUP_ID, CodegenConstants.GROUP_ID_DESC).defaultValue(DEFAULT_GROUP_ID));
        cliOptions.add(new CliOption(CodegenConstants.ARTIFACT_ID, CodegenConstants.ARTIFACT_DESCRIPTION).defaultValue(DEFAULT_ARTIFACT_ID));
        cliOptions.add(new CliOption(CodegenConstants.ARTIFACT_VERSION, CodegenConstants.ARTIFACT_VERSION).defaultValue(DEFAULT_ARTIFACT_VERSION));

        supportsInheritance = true;

        languageSpecificPrimitives = Sets.newHashSet("String",
                "boolean",
                "Boolean",
                "Double",
                "double",
                "Integer",
                "integer",
                "Long",
                "long",
                "Float",
                "float",
                "Object",
                "byte[]"
        );

        enablePostProcessFile = true;

        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("pom.mustache", "pom.xml"));

        instantiationTypes.put("array", "ArrayList");
        instantiationTypes.put("set", "HashSet");
        instantiationTypes.put("map", "HashMap");

        typeMapping.put("date", "LocalDate");
        typeMapping.put("DateTime", "OffsetDateTime");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("file", "byte[]");

        importMapping.put("UUID", "java.util.UUID");
        importMapping.put("LocalDate", "java.time.LocalDate");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");

        importMapping.put("List", "java.util.List");
        importMapping.put("Array", "java.util.List");
        importMapping.put("ArrayList", "java.util.ArrayList");
        importMapping.put("Map", "java.util.Map");
        importMapping.put("HashMap", "java.util.HashMap");
        importMapping.put("Set", "java.util.Set");
        importMapping.put("HashSet", "java.util.HashSet");
    }

    private String artifactIdToPackageName(String artifactId) {
        return artifactId.replace("-", "");
    }

    @Override
    public void processOpts() {
        super.processOpts();

        additionalProperties.putIfAbsent(CodegenConstants.GROUP_ID, DEFAULT_GROUP_ID);
        additionalProperties.putIfAbsent(CodegenConstants.ARTIFACT_ID, DEFAULT_ARTIFACT_ID);
        additionalProperties.putIfAbsent(CodegenConstants.ARTIFACT_VERSION, DEFAULT_ARTIFACT_VERSION);

        this.apiPackage = additionalProperties.get(CodegenConstants.GROUP_ID) + "." +
                artifactIdToPackageName((String) additionalProperties.get(CodegenConstants.ARTIFACT_ID)) + "." + apiPackage;
        additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
        this.modelPackage = additionalProperties.get(CodegenConstants.GROUP_ID) + "." +
                artifactIdToPackageName((String) additionalProperties.get(CodegenConstants.ARTIFACT_ID)) + "." + modelPackage;
        additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);
    }

    @Override
    public String getSchemaType(Schema p) {
        String openAPIType = super.getSchemaType(p);

        // don't apply renaming on types from the typeMapping
        if (typeMapping.containsKey(openAPIType)) {
            return typeMapping.get(openAPIType);
        }

        if (null == openAPIType) {
            LOGGER.error("No Type defined for Schema {}", p);
        }
        return toModelName(openAPIType);
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        Schema<?> schema = unaliasSchema(p);
        Schema<?> target = ModelUtils.isGenerateAliasAsModel() ? p : schema;
        if (ModelUtils.isArraySchema(target)) {
            Schema<?> items = getSchemaItems((ArraySchema) schema);
            return getSchemaType(target) + "<" + getTypeDeclaration(items) + ">";
        } else if (ModelUtils.isMapSchema(target)) {
            // Note: ModelUtils.isMapSchema(p) returns true when p is a composed schema that also defines
            // additionalproperties: true
            Schema<?> inner = ModelUtils.getAdditionalProperties(target);
            if (inner == null) {
                LOGGER.error("`{}` (map property) does not have a proper inner type defined. Default to type:string", p.getName());
                inner = new StringSchema().description("TODO default missing map inner type to string");
                p.setAdditionalProperties(inner);
            }
            return getSchemaType(target) + "<String, " + getTypeDeclaration(inner) + ">";
        }
        return super.getTypeDeclaration(target);
    }

    @Override
    public CodegenModel fromModel(String name, Schema schema) {
        var model = super.fromModel(name, schema);

        return model;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        if ("array".equals(property.containerType)) {
            model.imports.add("ArrayList");
        } else if ("set".equals(property.containerType)) {
            model.imports.add("HashSet");
        } else if ("map".equals(property.containerType)) {
            model.imports.add("HashMap");
        }
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        return postProcessModelsEnum(objs);
    }

    @Override
    public String toEnumName(CodegenProperty property) {
        return sanitizeName(camelize(property.name)) + "Enum";
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (enumNameMapping.containsKey(value)) {
            return enumNameMapping.get(value);
        }

        if (value.length() == 0) {
            return "EMPTY";
        }

        // for symbol, e.g. $, #
        if (getSymbolName(value) != null) {
            return getSymbolName(value).toUpperCase(Locale.ROOT);
        }

        if (" ".equals(value)) {
            return "SPACE";
        }

        // number
        if ("Integer".equals(datatype) || "Long".equals(datatype) ||
                "Float".equals(datatype) || "Double".equals(datatype) || "BigDecimal".equals(datatype)) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        // string
        String var = value.replaceAll("\\W+", "_").toUpperCase(Locale.ROOT);
        if (var.matches("\\d.*")) {
            return "_" + var;
        } else {
            return var;
        }
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        final OperationMap operations = objs.getOperations();
        if (operations != null) {
            final List<CodegenOperation> ops = operations.getOperation();
            for (final CodegenOperation operation : ops) {
                final List<CodegenResponse> responses = operation.responses;
                if (responses != null) {
                    for (final CodegenResponse resp : responses) {
                        if ("0".equals(resp.code)) {
                            resp.code = "200";
                        }
                        doDataTypeAssignment(resp.dataType, new DataTypeAssigner() {
                            @Override
                            public void setReturnType(final String returnType) {
                                resp.dataType = returnType;
                            }

                            @Override
                            public void setReturnContainer(final String returnContainer) {
                                resp.containerType = returnContainer;
                            }

                            @Override
                            public void setIsVoid(boolean isVoid) {
                                resp.isVoid = isVoid;
                            }
                        });
                    }
                }

                doDataTypeAssignment(operation.returnType, new DataTypeAssigner() {

                    @Override
                    public void setReturnType(final String returnType) {
                        operation.returnType = returnType;
                    }

                    @Override
                    public void setReturnContainer(final String returnContainer) {
                        operation.returnContainer = returnContainer;
                    }

                    @Override
                    public void setIsVoid(boolean isVoid) {
                        operation.isVoid = isVoid;
                    }
                });

//                prepareVersioningParameters(ops);
//                handleImplicitHeaders(operation);
            }
            // The tag for the controller is the first tag of the first operation
            final CodegenOperation firstOperation = ops.get(0);
            final Tag firstTag = firstOperation.tags.get(0);
            final String firstTagName = firstTag.getName();
            // But use a sensible tag name if there is none
            objs.put("tagName", "default".equals(firstTagName) ? firstOperation.baseName : firstTagName);
            objs.put("tagDescription", escapeText(firstTag.getDescription()));
        }

        removeImport(objs, "java.util.List");

        return objs;
    }

    private void doDataTypeAssignment(String returnType, DataTypeAssigner dataTypeAssigner) {
        final String rt = returnType;
        if (rt == null) {
            dataTypeAssigner.setReturnType("Void");
            dataTypeAssigner.setIsVoid(true);
        } else if (rt.startsWith("List") || rt.startsWith("java.util.List")) {
            final int start = rt.indexOf("<");
            final int end = rt.lastIndexOf(">");
            if (start > 0 && end > 0) {
                dataTypeAssigner.setReturnType(rt.substring(start + 1, end).trim());
                dataTypeAssigner.setReturnContainer("List");
            }
        } else if (rt.startsWith("Map") || rt.startsWith("java.util.Map")) {
            final int start = rt.indexOf("<");
            final int end = rt.lastIndexOf(">");
            if (start > 0 && end > 0) {
                dataTypeAssigner.setReturnType(rt.substring(start + 1, end).split(",", 2)[1].trim());
                dataTypeAssigner.setReturnContainer("Map");
            }
        } else if (rt.startsWith("Set") || rt.startsWith("java.util.Set")) {
            final int start = rt.indexOf("<");
            final int end = rt.lastIndexOf(">");
            if (start > 0 && end > 0) {
                dataTypeAssigner.setReturnType(rt.substring(start + 1, end).trim());
                dataTypeAssigner.setReturnContainer("Set");
            }
        }
    }

    private interface DataTypeAssigner {
        void setReturnType(String returnType);

        void setReturnContainer(String returnContainer);

        void setIsVoid(boolean isVoid);
    }

    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    public String getName() {
        return "fimo-spring";
    }

    public String getHelp() {
        return "Generates a fimo-spring client.";
    }

    @Override
    public String modelFileFolder() {
        return (outputFolder + File.separator + SOURCE_FOLDER + File.separator + modelPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
    }

    @Override
    public String apiFileFolder() {
        return (outputFolder + File.separator + SOURCE_FOLDER + File.separator + apiPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
    }
}
