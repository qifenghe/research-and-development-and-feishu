package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ProcessRevision.ProcessRevisionSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import org.springframework.util.AntPathMatcher;

/** A read-only index of accessible formal revisions, not a second SOP data store. */
@Service
public class SopCatalogService {
    private final JdbcTemplate jdbc;
    private final ProcessRevisionService revisions;
    private final RolePermissionService permissions;

    public SopCatalogService(JdbcTemplate jdbc, ProcessRevisionService revisions, RolePermissionService permissions) {
        this.jdbc = jdbc; this.revisions = revisions; this.permissions = permissions;
    }

    // Each revision read owns its read-only transaction: an inaccessible form must not
    // mark a shared outer transaction rollback-only while indexing the remaining forms.
    public Page list(String keyword, int page, int size, SessionPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.name() == null || principal.role() == null)
            throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "请先登录");
        if (!List.of("RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER").contains(principal.role()))
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前角色无权查看正式工艺");
        if (page < 1 || size < 1 || size > 100)
            throw new BusinessException("SOP_PAGE_INVALID", "分页参数不正确");
        var rules = permissions.rolePermissions(principal.role()).permissions();
        var matcher = new AntPathMatcher();
        BiPredicate<String,String> allowed = (method,path) -> rules.stream().anyMatch(rule -> rule.enabled()
                && method.equalsIgnoreCase(rule.httpMethod()) && matcher.match(rule.pathPattern(), path));
        var candidates = jdbc.query("""
                select f.id, f.task_id, f.product_name, v.specification, t.assignee_name
                from experiment_form f join sample_version v on v.id = f.version_id
                join rnd_task t on t.id = f.task_id
                where locate(lower(?), lower(f.product_name)) > 0
                and exists(select 1 from experiment_process_revision r where r.experiment_form_id = f.id)
                """, (rs, row) -> new Candidate(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                keyword == null ? "" : keyword.trim());
        var items = new ArrayList<Item>();
        for (var form : candidates) {
            boolean assistant="RND_ASSISTANT".equals(principal.role());
            var base = assistant?"/api/v1/sops/"+form.id()+"/revisions":"/api/v1/experiment-forms/" + form.id() + "/process-plan/revisions";
            if (!assistant && !allowed.test("GET", base)) continue;
            List<ProcessRevisionSummary> visible;
            try { visible = assistant?revisions.list(form.id()):revisions.catalogList(form.id(), principal); }
            catch (BusinessException e) {
                if ("PROCESS_PLAN_FORM_FORBIDDEN".equals(e.code())) continue;
                throw e;
            }
            visible = visible.stream().filter(r -> allowed.test("GET", base + "/" + r.id())).toList();
            if (visible.isEmpty()) continue;
            var latest = visible.get(0);
            var entries = visible.stream().map(r -> new Revision(r.id(), r.revisionNo(), r.sourceRevisionId(), r.changeReason(),
                    r.submittedBy(), r.submittedAt(), r.snapshotHash(),
                    (assistant || "RND_DIRECTOR".equals(principal.role()) || "RND_ENGINEER".equals(principal.role()))
                            && allowed.test("POST", base + "/" + r.id() + "/artifacts"))).toList();
            items.add(new Item(form.id(), form.taskId(), form.productName(), form.specification(), form.owner(),
                    latest.id(), latest.revisionNo(), latest.submittedAt(), entries.get(0).canGenerate(), entries));
        }
        items.sort(Comparator.comparing(Item::updatedAt).reversed().thenComparing(Item::formId));
        long offset = (long) (page - 1) * size;
        return new Page(items.stream().skip(offset).limit(size).toList(), items.size(), page, size);
    }

    private record Candidate(String id, String taskId, String productName, String specification, String owner) {}
    public record Item(String formId, String taskId, String productName, String specification, String owner,
                       String revisionId, int revisionNo, String updatedAt, boolean canGenerate,
                       List<Revision> revisions) {}
    public record Revision(String id, int revisionNo, String sourceRevisionId, String changeReason,
                           String submittedBy, String submittedAt, String snapshotHash, boolean canGenerate) {}
    public record Page(List<Item> items, long total, int page, int size) {}
}
