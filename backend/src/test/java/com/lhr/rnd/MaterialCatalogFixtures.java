package com.lhr.rnd;

import com.lhr.rnd.model.ProcessPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;

/** Gives valid workflow fixtures a real catalog identity; never used by production code. */
public final class MaterialCatalogFixtures {
    private MaterialCatalogFixtures() {}
    public static ProcessPlan.StepMaterial material(JdbcTemplate db,String id,int sequence,String role,String code,String name,String state,
            BigDecimal weight,String formulaId,String remark) {
        return material(db,id,sequence,role,code,name,state,weight,formulaId,remark,"EXTERNAL",null);
    }
    public static ProcessPlan.StepMaterial material(JdbcTemplate db,String id,int sequence,String role,String code,String name,String state,
            BigDecimal weight,String formulaId,String remark,String source,String outputId) {
        return material(db,id,sequence,role,code,name,state,weight,formulaId,remark,source,outputId,null,null);
    }
    public static ProcessPlan.StepMaterial material(JdbcTemplate db,String id,int sequence,String role,String code,String name,String state,
            BigDecimal weight,String formulaId,String remark,String source,String outputId,String timing,String method) {
        String catalogId="STEP_OUTPUT".equals(source)?null:identity(db,code,name);
        return new ProcessPlan.StepMaterial(id,sequence,role,code,name,state,weight,formulaId,remark,source,outputId,timing,method,catalogId,null,catalogId==null?null:"kg");
    }
    public static String identity(JdbcTemplate db,String code,String name) {
        var normalized=code==null||code.isBlank()?"FIX-"+Integer.toUnsignedString(name.hashCode()):code.trim().toUpperCase(java.util.Locale.ROOT);
        var ids=db.queryForList("select id from material_catalog where code=?",String.class,normalized);
        if(!ids.isEmpty()) {
            db.update("update material_catalog set name=?,status='ACTIVE' where id=?",name,ids.get(0));
            return ids.get(0);
        }
        var id="FIX-"+UUID.randomUUID();
        db.update("insert into material_catalog(id,code,name,category,unit,status,version,created_at,updated_at) values(?,?,?,'RAW','kg','ACTIVE',0,current_timestamp,current_timestamp)",id,normalized,name);
        return id;
    }
    public static String json(JdbcTemplate db,String json) {
        try {
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var tree=mapper.readTree(json);
            for(var major:tree.path("majorProcesses"))for(var step:major.path("steps"))for(var material:step.path("materials")) {
                if("STEP_OUTPUT".equals(material.path("sourceType").asText())||!material.path("catalogMaterialId").asText().isBlank())continue;
                ((com.fasterxml.jackson.databind.node.ObjectNode)material).put("catalogMaterialId",identity(db,material.path("materialCode").asText(),material.path("materialName").asText()));
            }
            return mapper.writeValueAsString(tree);
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
}
