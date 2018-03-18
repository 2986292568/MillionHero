package pattern.impl;

import ocr.OCR;
import pattern.Pattern;
import search.Search;
import search.impl.SearchFactory;
import utils.ImageHelper;
import pojo.Information;
import utils.Utils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.*;

/**
 * Created by lingfengsan on 2018/1/18.
 *
 * @author lingfengsan
 */
public class CommonPattern implements Pattern {
    private static final String QUESTION_FLAG = "?";
    private static int[] startX = {100, 100, 80};
    private static int[] startY = {300, 300, 300};
    private static int[] width = {900, 900, 900};
    private static int[] height = {900, 900, 700};
    private ImageHelper imageHelper = new ImageHelper();
    private SearchFactory searchFactory = new SearchFactory();
    private int patterSelection;
    private int searchSelection;
    private OCR ocr;
    private Utils utils;
    private ExecutorService pool = Executors.newFixedThreadPool(7);

    public void setPatterSelection(int patterSelection) {
        switch (patterSelection) {
            case 2: {
                System.out.println("欢迎进入冲顶大会");
                break;
            }
            default: {
                System.out.println("欢迎进入百万英雄");
                break;
            }
        }
        this.patterSelection = patterSelection;
    }

    public void setSearchSelection(int searchSelection) {
        switch (searchSelection) {
            case 2: {
                System.out.println("欢迎使用搜狗搜索");
                break;
            }
            default: {
                System.out.println("欢迎使用百度搜索");
                break;
            }
        }
        this.searchSelection = searchSelection;
    }

    public void setOcr(OCR ocr) {
        this.ocr = ocr;
    }

    public void setUtils(Utils utils) {
        this.utils = utils;
    }


    @Override
    public String run() throws UnsupportedEncodingException {
        //       记录开始时间
        long startTime;
        //       记录结束时间
        long endTime;
        StringBuilder sb = new StringBuilder();
        startTime = System.currentTimeMillis();
        //获取图片
        String imagePath = utils.getImage();
        System.out.println("图片获取成功");
        //裁剪图片
        imageHelper.cutImage(imagePath, imagePath,
                startX[patterSelection], startY[patterSelection], width[patterSelection], height[patterSelection]);
        //图像识别
        Long beginOfDetect = System.currentTimeMillis();
        String questionAndAnswers = ocr.getOCR(new File(imagePath));
        sb.append(questionAndAnswers);
        System.out.println("识别成功");
        System.out.println("识别时间：" + (System.currentTimeMillis() - beginOfDetect));
        if (questionAndAnswers == null || !questionAndAnswers.contains(QUESTION_FLAG)) {
            sb.append("问题识别失败，输入回车继续运行\n");
            return sb.toString();
        }
        //获取问题和答案
        System.out.println("检测到题目");
        Information information = utils.getInformation(questionAndAnswers);
        String question = information.getQuestion();
        String[] answers = information.getAns();
        if (question == null) {
            sb.append("问题不存在，继续运行\n");
            return sb.toString();
        } else if (answers.length < 1) {
            sb.append("检测不到答案，继续运行\n");
            return sb.toString();
        }
        sb.append("问题:").append(question).append("\n");
        sb.append("答案：\n");
        for (String answer : answers) {
            sb.append(answer).append("\n");
        }
        //搜索
        long countQuestion = 1;
        int numOfAnswer = answers.length > 3 ? 4 : answers.length;
        long[] countQA = new long[numOfAnswer];
        long[] countAnswer = new long[numOfAnswer];

        int maxIndex = 0;
        Search[] searchQA = new Search[numOfAnswer];
        Search[] searchAnswers = new Search[numOfAnswer];
        FutureTask[] futureQuestion = new FutureTask[1];
        FutureTask[] futureQA = new FutureTask[numOfAnswer];
        FutureTask[] futureAnswers = new FutureTask[numOfAnswer];
        futureQuestion[0] = new FutureTask<Long>(searchFactory.getSearch(searchSelection, question, true));
        pool.execute(futureQuestion[0]);
        for (int i = 0; i < numOfAnswer; i++) {
            searchQA[i] = searchFactory.getSearch(searchSelection, (question + " " + answers[i]), false);
            searchAnswers[i] = searchFactory.getSearch(searchSelection, answers[i], false);

            futureQA[i] = new FutureTask<Long>(searchQA[i]);
            futureAnswers[i] = new FutureTask<Long>(searchAnswers[i]);
            pool.execute(futureQA[i]);
            pool.execute(futureAnswers[i]);
        }
        try {

            while (true) {
                if (futureQuestion[0].isDone()) {
                    break;
                }
            }
            countQuestion = (Long) futureQuestion[0].get();
            for (int i = 0; i < numOfAnswer; i++) {
                while (true) {
                    if (futureAnswers[i].isDone() && futureQA[i].isDone()) {
                        break;
                    }
                }
                countQA[i] = (Long) futureQA[i].get();
                countAnswer[i] = (Long) futureAnswers[i].get();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        float[] ans = new float[numOfAnswer];
        for (int i = 0; i < numOfAnswer; i++) {
            ans[i] = (float) countQA[i] / (float) (countQuestion * countAnswer[i]);
            maxIndex = (ans[i] > ans[maxIndex]) ? i : maxIndex;
        }
        //根据pmi值进行打印搜索结果
        int[] rank = Utils.rank(ans);
        for (int i : rank) {

            sb.append(answers[i]);
            sb.append(" countQA:").append(countQA[i]);
            sb.append(" countAnswer:").append(countAnswer[i]);
            sb.append(" ans:").append(ans[i]).append("\n");
        }

        sb.append("--------最终结果-------\n");
        sb.append(answers[maxIndex]);
        endTime = System.currentTimeMillis();
        float excTime = (float) (endTime - startTime) / 1000;

        sb.append("执行时间：").append(excTime).append("s").append("\n");
        return sb.toString();
    }
}
