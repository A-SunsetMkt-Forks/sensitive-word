package com.github.houbb.sensitive.word.core;

import com.github.houbb.heaven.util.guava.Guavas;
import com.github.houbb.heaven.util.util.CollectionUtil;
import com.github.houbb.sensitive.word.api.*;
import com.github.houbb.sensitive.word.api.context.InnerSensitiveWordContext;
import com.github.houbb.sensitive.word.constant.enums.WordValidModeEnum;
import com.github.houbb.sensitive.word.support.check.WordCheckResult;
import com.github.houbb.sensitive.word.support.result.WordLengthResult;
import com.github.houbb.sensitive.word.support.result.WordResult;
import com.github.houbb.sensitive.word.utils.InnerWordFormatUtils;

import java.util.List;
import java.util.Map;

/**
 * 默认实现
 *
 * @since 0.3.2
 */
public class SensitiveWord extends AbstractSensitiveWord {

    /**
     * 0.3.2
     */
    private static final ISensitiveWord INSTANCE = new SensitiveWord();

    public static ISensitiveWord getInstance() {
        return INSTANCE;
    }

    @Override
    protected List<IWordResult> doFindAll(String string, IWordContext context) {
        return innerSensitiveWords(string, WordValidModeEnum.FAIL_OVER, context);
    }

    @Override
    protected IWordResult doFindFirst(String string, IWordContext context) {
        List<IWordResult> wordResults = innerSensitiveWords(string, WordValidModeEnum.FAIL_FAST, context);
        if (!CollectionUtil.isEmpty(wordResults)) {
            return wordResults.get(0);
        }
        return null;
    }


    /**
     * 获取敏感词列表
     *
     * @param text 文本
     * @param modeEnum 模式
     * @return 结果列表
     * @since 0.0.1
     */
    private List<IWordResult> innerSensitiveWords(final String text,
            final WordValidModeEnum modeEnum,
            final IWordContext context) {
        //1. 是否存在敏感词，如果比存在，直接返回空列表
        final IWordCheck sensitiveCheck = context.sensitiveCheck();
        List<IWordResult> resultList = Guavas.newArrayList();

        //TODO: 这里拆分为2个部分，从而保障性能。但是要注意处理下标的问题。
        //1. 原始的敏感词部分
        //2. email/url/num 的单独一次遍历处理。
        final Map<Character, Character> characterCharacterMap = context.wordFormatText().format(text, context);
        final InnerSensitiveWordContext checkContext = InnerSensitiveWordContext.newInstance()
                .originalText(text)
                .wordContext(context)
                .modeEnum(WordValidModeEnum.FAIL_OVER)
                .formatCharMapping(characterCharacterMap);
        final IWordResultCondition wordResultCondition = context.wordResultCondition();

        for (int i = 0; i < text.length(); i++) {
            // v0.21.0 白名单跳过
            WordCheckResult checkResult = sensitiveCheck.sensitiveCheck(i, checkContext);
            int wordLengthAllow = checkResult.wordLengthResult().wordAllowLen();
            int wordLengthDeny = checkResult.wordLengthResult().wordDenyLen();

            //如果命中的白名单长度小于黑名单，则直接对黑名单的敏感词进行保存
            if (wordLengthAllow < wordLengthDeny) {
                // 保存敏感词
                WordResult wordResult = WordResult.newInstance()
                        .startIndex(i)
                        .endIndex(i + wordLengthDeny)
                        .type(checkResult.type())
                        .word(checkResult.wordLengthResult().wordDeny());

                //v0.13.0 添加判断
                if (wordResultCondition.match(wordResult, text, modeEnum, context)) {
                    resultList.add(wordResult);
                    // 快速返回
                    if (WordValidModeEnum.FAIL_FAST.equals(modeEnum)) {
                        break;
                    }
                }
                // 增加 i 的步长
                // 为什么要-1，因为默认就会自增1
                // TODO: 这里可以根据字符串匹配算法优化。
                i += wordLengthDeny - 1;
            } else {
                //如果命中的白名单长度大于黑名单长度，则跳过白名单个字符
                i += Math.max(0, wordLengthAllow - 1);
            }
        }

        return resultList;
    }

}
