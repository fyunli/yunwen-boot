package li.fyun.commons.rule.engine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import li.fyun.commons.rule.enums.RuleItemRelation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jeasy.rules.api.*;
import org.jeasy.rules.core.DefaultRulesEngine;

import org.jeasy.rules.support.composite.CompositeRule;
import org.mvel2.MVEL;

import java.util.List;
import java.util.Map;

@Slf4j
public final class RuleHelper {

    public static final String RESULT_PREFIX = "result";

    public static Facts fire(RulesWrapper rulesWrapper,
                             Map<String, Object> params,
                             RuleItemRelation ruleItemRelation,
                             RuleListener... ruleListeners) {
        if (rulesWrapper == null || rulesWrapper.getRules() == null) {
            return null;
        }

        RulesEngine rulesEngine = createRuleEngine(ruleItemRelation, ruleListeners);
        Facts facts = createFacts(params);
        rulesEngine.fire(rulesWrapper.getRules(), facts);
        return facts;
    }

    public static Facts createFacts(Map<String, Object> values) {
        Facts facts = new Facts();
        if (MapUtils.isNotEmpty(values)) {
            values.forEach((k, v) -> {
                if (v != null) {
                    facts.put(k, v);
                }
            });
        }
        facts.put(RESULT_PREFIX, Maps.newLinkedHashMap());
        return facts;
    }

    public static RulesEngine createRuleEngine(RuleItemRelation ruleItemRelation, RuleListener... ruleListeners) {
        RulesEngineParameters parameters = new RulesEngineParameters();
        if (RuleItemRelation.AND.equals(ruleItemRelation)) {
            parameters.skipOnFirstAppliedRule(false);
        } else {
            parameters.skipOnFirstAppliedRule(true);
        }
        DefaultRulesEngine rulesEngine = new DefaultRulesEngine(parameters);
        if (ArrayUtils.isNotEmpty(ruleListeners)) {
            for (RuleListener ruleListener : ruleListeners) {
                rulesEngine.registerRuleListener(ruleListener);
            }
        }
        return rulesEngine;
    }

    public static <E extends IRule> RulesWrapper getRules(List<E> ruleItems) {
        if (CollectionUtils.isEmpty(ruleItems)) {
            return null;
        }

        Rules rules = new Rules();
        ruleItems.forEach(i -> {
            rules.register(i.asRule().getRule());
            log.debug("rule {} registered, condition {}, action {}",
                    i.getCode(), i.getConditionExpression(), i.getActionExpression());
        });
        return new RulesWrapper(rules);
    }

    public static RuleWrapper asRule(IRule aRule, int priority) {
        Rule rule = new PlusFactsMVELRule()
                .setPlusFacts(aRule.getAttachFacts())
                .name(aRule.getCode())
                .description(aRule.getDescription())
                .priority(priority)
                .when(aRule.getConditionExpression())
                .then(aRule.getActionExpression());

        List<IRule> children = aRule.getChildren();
        if (CollectionUtils.isNotEmpty(aRule.getChildren())) {
            CompositeRule unitRuleGroup = new PostConditionalRuleGroup(
                    aRule.getCode() + "_" + RandomStringUtils.randomAlphabetic(6),
                    aRule.getPriority(),
                    rule
            );
            children.forEach(c -> {
                RuleWrapper subRule = c.asRule(c.getPriority() + aRule.getPriority());
                unitRuleGroup.addRule(subRule.getRule());
                log.debug("child rule {} registered, condition {}, action {}",
                        c.getCode(), c.getConditionExpression(), c.getActionExpression());
            });
            return new RuleWrapper(unitRuleGroup);
        } else {
            return new RuleWrapper(rule);
        }
    }

    public static Facts testSingleRule(IRule rule, Map<String, Object> param) {
        RulesWrapper rules = RuleHelper.getRules(ImmutableList.of(rule));
        return RuleHelper.fire(rules, param, RuleItemRelation.AND);
    }

    public static boolean testCondition(String condition, Map<String, Object> params) {
        return (Boolean) MVEL.eval(condition, params);
    }

    public static Object testFormula(String formula, Map<String, Object> params) {
        return MVEL.eval(formula, params);
    }

}
