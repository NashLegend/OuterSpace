package net.nashlegend.sourcewall.request.api;

import android.text.TextUtils;

import net.nashlegend.sourcewall.model.AceModel;
import net.nashlegend.sourcewall.model.PrepareData;
import net.nashlegend.sourcewall.model.Question;
import net.nashlegend.sourcewall.model.QuestionAnswer;
import net.nashlegend.sourcewall.model.SubItem;
import net.nashlegend.sourcewall.model.UComment;
import net.nashlegend.sourcewall.request.HttpFetcher;
import net.nashlegend.sourcewall.request.RequestCache;
import net.nashlegend.sourcewall.request.ResultObject;
import net.nashlegend.sourcewall.util.Config;
import net.nashlegend.sourcewall.util.MDUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 单个答案地址。http://www.guokr.com/answer/782227/
 * 缓存key规则：
 * 热门问答的key是 question.hottest
 * 精彩回答的key是 question.highlight
 * 按tag加载的问题的key是 question.{tag}
 */
public class QuestionAPI extends APIBase {
    private static int maxImageWidth = 240;
    private static String prefix = "<div class=\"ZoomBox\"><div class=\"content-zoom ZoomIn\">";
    private static String suffix = "</div></div>";

    public static ResultObject<ArrayList<Question>> getCachedQuestionList(SubItem subItem) {
        ResultObject<ArrayList<Question>> cachedResultObject = new ResultObject<>();
        String key = "question." + subItem.getValue();
        String content = RequestCache.getInstance().getStringFromCache(key);
        if (!TextUtils.isEmpty(content)) {
            if (subItem.getType() == SubItem.Type_Collections) {
                cachedResultObject = parseQuestionsHtml(content);
            } else {
                cachedResultObject = parseQuestionsListJson(content);
            }
        }
        return cachedResultObject;
    }

    /**
     * 返回所有我感兴趣的标签
     *
     * @return ResultObject，resultObject.result是ArrayList[SubItem]
     */
    public static ResultObject<ArrayList<SubItem>> getAllMyTags() {
        ResultObject<ArrayList<SubItem>> resultObject = new ResultObject<>();
        String pageUrl = "http://www.guokr.com/ask/i/" + UserAPI.getUserID() + "/following_tags/";
        ArrayList<SubItem> subItems = new ArrayList<>();
        int numPages;
        try {
            String firstPage = HttpFetcher.get(pageUrl).toString();
            Document doc1 = Jsoup.parse(firstPage);
            Elements as = doc1.getElementsByClass("gpages");
            if (as.size() == 0) {
                numPages = 1;
            } else {
                numPages = Integer.valueOf(as.get(0).getElementsByTag("a").last().attr("href").replaceAll("^\\S+?page=", ""));
            }
            Elements lis = doc1.getElementsByClass("join-list").get(0).getElementsByTag("li");
            //第一页
            for (int i = 0; i < lis.size(); i++) {
                Element element = lis.get(i).getElementsByClass("join-list-desc").get(0);
                String groupName = element.getElementsByTag("a").text();
                SubItem subItem = new SubItem(SubItem.Section_Question, SubItem.Type_Single_Channel, groupName, groupName);
                subItems.add(subItem);
            }
            if (numPages > 1) {
                for (int j = 2; j <= numPages; j++) {
                    Thread.sleep(100);
                    String url = pageUrl + "?page=" + j;
                    Document pageDoc = Jsoup.parse(HttpFetcher.get(url).toString());
                    Elements lis2 = pageDoc.getElementsByClass("join-list").get(0).getElementsByTag("li");
                    for (int i = 0; i < lis2.size(); i++) {
                        Element element = lis2.get(i).getElementsByClass("join-list-desc").get(0);
                        String groupName = element.getElementsByTag("a").text();
                        SubItem subItem = new SubItem(SubItem.Section_Question, SubItem.Type_Single_Channel, groupName, groupName);
                        subItems.add(subItem);
                    }
                }
            }
            resultObject.ok = true;
            resultObject.result = subItems;
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 根据tag获取相关问题，json格式
     * 比html还特么浪费流量，垃圾数据太多了
     * resultObject.result是ArrayList[Question]
     *
     * @param tag    标签名
     * @param offset 从第几个开始加载
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Question>> getQuestionsByTagFromJsonUrl(String tag, int offset) {
        ResultObject<ArrayList<Question>> resultObject = new ResultObject<>();
        try {
            String url = "http://apis.guokr.com/ask/question.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("retrieve_type", "by_tag");
            pairs.put("tag_name", tag);
            pairs.put("limit", "20");
            pairs.put("offset", offset + "");
            String jString = HttpFetcher.get(url, pairs).toString();
            resultObject = parseQuestionsListJson(jString);

            if (resultObject.ok && offset == 0) {
                //请求成功则缓存之
                String key = "question." + URLDecoder.decode(tag, "utf-8");
                RequestCache.getInstance().addStringToCacheForceUpdate(key, jString);
            }

        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 解析QuestionList的json
     *
     * @param jString json
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Question>> parseQuestionsListJson(String jString) {
        ResultObject<ArrayList<Question>> resultObject = new ResultObject<>();
        try {
            if (jString != null) {
                ArrayList<Question> questions = new ArrayList<>();
                JSONArray results = getUniversalJsonArray(jString, resultObject);
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject jsonObject = results.getJSONObject(i);
                        Question question = new Question();
                        question.setAnswerNum(getJsonInt(jsonObject, "answers_count"));
                        question.setAuthor(jsonObject.getJSONObject("author").getString("nickname"));
                        question.setAuthorID(jsonObject.getJSONObject("author").getString("url").replaceAll("\\D+", ""));
                        question.setAuthorAvatarUrl(jsonObject.getJSONObject("author").getJSONObject("avatar").getString("large").replaceAll("\\?.*$", ""));
                        question.setSummary(getJsonString(jsonObject, "summary"));
                        question.setDate(parseDate(getJsonString(jsonObject, "date_created")));
                        question.setFollowNum(getJsonInt(jsonObject, "followers_count"));
                        question.setId(getJsonString(jsonObject, "id"));
                        question.setTitle(getJsonString(jsonObject, "question"));
                        question.setUrl(getJsonString(jsonObject, "url"));
                        questions.add(question);
                    }
                    resultObject.ok = true;
                    resultObject.result = questions;
                }
            }

        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }


    /**
     * 返回热门回答问题列表，解析html获得
     *
     * @param pageNo 页码
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Question>> getHotQuestions(int pageNo) {
        String url = "http://m.guokr.com/ask/hottest/?page=" + pageNo;
        ResultObject<ArrayList<Question>> resultObject = new ResultObject<>();
        try {
            String html = HttpFetcher.get(url).toString();
            resultObject = parseQuestionsHtml(html);
            if (resultObject.ok && pageNo == 1) {
                RequestCache.getInstance().addStringToCacheForceUpdate("question.hottest", html);
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 返回精彩回答问题列表，解析html所得
     *
     * @param pageNo 页码
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Question>> getHighlightQuestions(int pageNo) {
        String url = "http://m.guokr.com/ask/highlight/?page=" + pageNo;
        ResultObject<ArrayList<Question>> resultObject = new ResultObject<>();
        try {
            String html = HttpFetcher.get(url).toString();
            resultObject = parseQuestionsHtml(html);
            if (resultObject.ok && pageNo == 1) {
                RequestCache.getInstance().addStringToCacheForceUpdate("question.highlight", html);
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 解析html页面获得问题列表
     *
     * @param html 页面内容
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Question>> parseQuestionsHtml(String html) {
        ResultObject<ArrayList<Question>> resultObject = new ResultObject<>();
        try {
            ArrayList<Question> questions = new ArrayList<>();
            Document doc = Jsoup.parse(html);
            Elements elements = doc.getElementsByClass("ask-list");
            if (elements.size() == 1) {
                Elements questioList = elements.get(0).getElementsByTag("li");
                for (int i = 0; i < questioList.size(); i++) {
                    Question item = new Question();
                    Element element = questioList.get(i);
                    Element link = element.getElementsByTag("a").get(0);
                    String title = link.getElementsByTag("h4").text();
                    String id = link.attr("href").replaceAll("\\D+", "");
                    String summary = link.getElementsByTag("p").text();
                    String l = link.getElementsByClass("ask-descrip").text().replaceAll("\\D+", "");
                    if (!TextUtils.isEmpty(l)) {
                        item.setRecommendNum(Integer.valueOf(l));
                    }
                    item.setTitle(title);
                    item.setId(id);
                    item.setSummary(summary);
                    item.setFeatured(true);
                    questions.add(item);
                }
                resultObject.ok = true;
                resultObject.result = questions;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }

        return resultObject;
    }

    /**
     * 返回问题内容,json格式
     *
     * @param id 问题ID
     *
     * @return ResultObject
     */
    public static ResultObject<Question> getQuestionDetailByID(String id) {
        String url = "http://apis.guokr.com/ask/question/" + id + ".json";
        return getQuestionDetailFromJsonUrl(url);
    }

    /**
     * 返回问题内容
     * resultObject.result是Question
     *
     * @param url 返回问题内容,json格式
     *
     * @return ResultObject
     */
    public static ResultObject<Question> getQuestionDetailFromJsonUrl(String url) {
        ResultObject<Question> resultObject = new ResultObject<>();
        try {
            Question question;
            ResultObject httpResult = HttpFetcher.get(url, null);
            resultObject.statusCode = httpResult.statusCode;
            if (resultObject.statusCode == 404) {
                return resultObject;
            }
            String jString = httpResult.toString();
            JSONObject result = getUniversalJsonObject(jString, resultObject);
            if (result != null) {
                question = new Question();
                question.setAnswerable(getJsonBoolean(result, "is_answerable"));//难道意味着已经回答了
                question.setAnswerNum(getJsonInt(result, "answers_count"));
                question.setCommentNum(getJsonInt(result, "replies_count"));
                question.setAuthor(result.getJSONObject("author").getString("nickname"));
                question.setAuthorID(result.getJSONObject("author").getString("url").replaceAll("\\D+", ""));
                question.setAuthorAvatarUrl(result.getJSONObject("author").getJSONObject("avatar").getString("large").replaceAll("\\?.*$", ""));
                question.setContent(getJsonString(result, "annotation_html").replaceAll("<img .*?/>", prefix + "$0" + suffix).replaceAll("style=\"max-width: \\d+px\"", "style=\"max-width: " + maxImageWidth + "px\""));
                question.setDate(parseDate(getJsonString(result, "date_created")));
                question.setFollowNum(getJsonInt(result, "followers_count"));
                question.setId(getJsonString(result, "id"));
                question.setRecommendNum(getJsonInt(result, "recommends_count"));
                question.setTitle(getJsonString(result, "question"));
                question.setUrl(getJsonString(result, "url"));
                resultObject.ok = true;
                resultObject.result = question;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }

        return resultObject;
    }

    /**
     * 获取问题的答案，json格式
     * resultObject.result是ArrayList[QuestionAnswer]
     *
     * @param id     问题id
     * @param offset 从第几个开始加载
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<AceModel>> getQuestionAnswers(String id, int offset) {
        ResultObject<ArrayList<AceModel>> resultObject = new ResultObject<>();
        try {
            ArrayList<AceModel> answers = new ArrayList<>();
            String url = "http://apis.guokr.com/ask/answer.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("retrieve_type", "by_question");
            pairs.put("question_id", id);
            pairs.put("limit", "20");
            pairs.put("offset", offset + "");
            String jString = HttpFetcher.get(url, pairs).toString();
            JSONArray comments = getUniversalJsonArray(jString, resultObject);
            if (comments != null) {
                for (int i = 0; i < comments.length(); i++) {
                    JSONObject jo = comments.getJSONObject(i);
                    QuestionAnswer ans = new QuestionAnswer();
                    JSONObject authorObject = getJsonObject(jo, "author");
                    boolean exists = getJsonBoolean(authorObject, "is_exists");
                    ans.setAuthorExists(exists);
                    if (exists) {
                        ans.setAuthor(getJsonString(authorObject, "nickname"));
                        ans.setAuthorTitle(getJsonString(authorObject, "title"));
                        ans.setAuthorID(getJsonString(authorObject, "url").replaceAll("\\D+", ""));
                        ans.setAuthorAvatarUrl(getJsonObject(authorObject, "avatar").getString("large").replaceAll("\\?.*$", ""));
                    } else {
                        ans.setAuthor("此用户不存在");
                    }
                    ans.setCommentNum(getJsonInt(jo, "replies_count"));
                    ans.setContent(getJsonString(jo, "html").replaceAll("<img .*?/>", prefix + "$0" + suffix).replaceAll("style=\"max-width: \\d+px\"", "style=\"max-width: " + maxImageWidth + "px\""));
                    ans.setDate_created(parseDate(getJsonString(jo, "date_created")));
                    ans.setDate_modified(parseDate(getJsonString(jo, "date_modified")));
                    ans.setHasDownVoted(getJsonBoolean(jo, "current_user_has_opposed"));
                    ans.setHasBuried(getJsonBoolean(jo, "current_user_has_buried"));
                    ans.setHasUpVoted(getJsonBoolean(jo, "current_user_has_supported"));
                    ans.setHasThanked(getJsonBoolean(jo, "current_user_has_thanked"));
                    ans.setID(getJsonString(jo, "id"));
                    ans.setQuestionID(getJsonString(jo, "question_id"));
                    ans.setUpvoteNum(getJsonInt(jo, "supportings_count"));
                    ans.setDownvoteNum(getJsonInt(jo, "opposings_count"));
                    answers.add(ans);
                }
                resultObject.ok = true;
                resultObject.result = answers;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 根据一条评论的id获取评论内容，主要应用于消息通知
     *
     * @param url 评论id
     *
     * @return resultObject resultObject.result是UComment
     */
    public static ResultObject<QuestionAnswer> getSingleAnswerFromRedirectUrl(String url) {
        //http://www.guokr.com/answer/654321/redirect/
        //http://www.guokr.com/answer/654321/
        return getSingleAnswerByID(url.replaceAll("\\D+", ""));
    }

    /**
     * 根据一条评论的id获取评论内容，主要应用于消息通知
     *
     * @param id 评论id
     *
     * @return resultObject resultObject.result是UComment
     */
    public static ResultObject<QuestionAnswer> getSingleAnswerByID(String id) {
        ResultObject<QuestionAnswer> resultObject = new ResultObject<>();
        String url = "http://apis.guokr.com/ask/answer.json";
        //url还有另一种形式，http://apis.guokr.com/ask/answer/999999.json
        //这样后面就不必带answer_id参数了
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("answer_id", id);
        try {
            String result = HttpFetcher.get(url, pairs).toString();
            JSONArray answerArray = getUniversalJsonArray(result, resultObject);
            if (answerArray != null && answerArray.length() > 0) {
                JSONObject answerObject = answerArray.getJSONObject(0);
                JSONObject questionObject = getJsonObject(answerObject, "question");
                String hostTitle = getJsonString(questionObject, "question");
                String hostID = getJsonString(questionObject, "id");

                boolean current_user_has_supported = getJsonBoolean(answerObject, "current_user_has_supported");
                boolean current_user_has_buried = getJsonBoolean(answerObject, "current_user_has_buried");
                boolean current_user_has_thanked = getJsonBoolean(answerObject, "current_user_has_thanked");
                boolean current_user_has_opposed = getJsonBoolean(answerObject, "current_user_has_opposed");

                JSONObject authorObject = getJsonObject(answerObject, "author");
                String author = getJsonString(authorObject, "nickname");
                String authorTitle = getJsonString(authorObject, "title");
                String authorID = getJsonString(authorObject, "url").replaceAll("\\D+", "");
                boolean is_exists = getJsonBoolean(authorObject, "is_exists");
                JSONObject avatarObject = getJsonObject(authorObject, "avatar");
                String avatarUrl = getJsonString(avatarObject, "large").replaceAll("\\?.*$", "");

                String date_created = parseDate(getJsonString(answerObject, "date_created"));
                String date_modified = parseDate(getJsonString(answerObject, "date_modified"));
                int replies_count = getJsonInt(answerObject, "replies_count");
                int supportings_count = getJsonInt(answerObject, "supportings_count");
                int opposings_count = getJsonInt(answerObject, "opposings_count");
                String content = getJsonString(answerObject, "html");

                QuestionAnswer answer = new QuestionAnswer();
                answer.setAuthorExists(is_exists);
                if (is_exists) {
                    answer.setAuthor(author);
                    answer.setAuthorTitle(authorTitle);
                    answer.setAuthorID(authorID);
                    answer.setAuthorAvatarUrl(avatarUrl);
                } else {
                    answer.setAuthor("此用户不存在");
                }
                answer.setCommentNum(replies_count);
                answer.setContent(content.replaceAll("<img .*?/>", prefix + "$0" + suffix).replaceAll("style=\"max-width: \\d+px\"", "style=\"max-width: " + maxImageWidth + "px\""));
                answer.setDate_created(date_created);
                answer.setDate_modified(date_modified);
                answer.setHasDownVoted(current_user_has_opposed);
                answer.setHasBuried(current_user_has_buried);
                answer.setHasUpVoted(current_user_has_supported);
                answer.setHasThanked(current_user_has_thanked);
                answer.setID(id);
                answer.setQuestionID(hostID);
                answer.setQuestion(hostTitle);
                answer.setUpvoteNum(supportings_count);
                answer.setDownvoteNum(opposings_count);
                resultObject.ok = true;
                resultObject.result = answer;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 返回问题的评论，json格式
     * resultObject.result是ArrayList[UComment]
     *
     * @param id     问题id
     * @param offset 从第几个开始加载
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<UComment>> getQuestionComments(String id, int offset) {
        ResultObject<ArrayList<UComment>> resultObject = new ResultObject<>();
        try {
            ArrayList<UComment> list = new ArrayList<>();
            String url = "http://www.guokr.com/apis/ask/question_reply.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("retrieve_type", "by_question");
            pairs.put("question_id", id);
            pairs.put("limit", "20");
            pairs.put("offset", offset + "");
            String jString = HttpFetcher.get(url, pairs).toString();
            JSONArray comments = getUniversalJsonArray(jString, resultObject);
            if (comments != null) {
                for (int i = 0; i < comments.length(); i++) {
                    JSONObject jsonObject = comments.getJSONObject(i);
                    UComment comment = new UComment();
                    comment.setAuthor(jsonObject.getJSONObject("author").getString("nickname"));
                    comment.setAuthorID(jsonObject.getJSONObject("author").getString("url").replaceAll("\\D+", ""));
                    comment.setAuthorAvatarUrl(jsonObject.getJSONObject("author").getJSONObject("avatar").getString("large").replaceAll("\\?.*$", ""));
                    comment.setContent(getJsonString(jsonObject, "text"));
                    comment.setDate(parseDate(getJsonString(jsonObject, "date_created")));
                    comment.setID(getJsonString(jsonObject, "id"));
                    comment.setHostID(getJsonString(jsonObject, "question_id"));
                    list.add(comment);
                }
                resultObject.ok = true;
                resultObject.result = list;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 返回答案的评论，json格式
     * resultObject.result是ArrayList[UComment]
     *
     * @param id     答案id
     * @param offset 从第几个开始加载
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<UComment>> getAnswerComments(String id, int offset) {
        ResultObject<ArrayList<UComment>> resultObject = new ResultObject<>();
        try {
            ArrayList<UComment> list = new ArrayList<>();
            String url = "http://www.guokr.com/apis/ask/answer_reply.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("retrieve_type", "by_answer");
            pairs.put("answer_id", id);
            pairs.put("limit", "20");
            pairs.put("offset", offset + "");
            String jString = HttpFetcher.get(url, pairs).toString();
            JSONArray comments = getUniversalJsonArray(jString, resultObject);
            if (comments != null) {
                for (int i = 0; i < comments.length(); i++) {
                    JSONObject jsonObject = comments.getJSONObject(i);
                    UComment comment = new UComment();

                    JSONObject authorObject = getJsonObject(jsonObject, "author");
                    boolean exists = getJsonBoolean(authorObject, "is_exists");
                    comment.setAuthorExists(exists);
                    if (exists) {
                        comment.setAuthor(getJsonString(authorObject, "nickname"));
                        comment.setAuthorID(getJsonString(authorObject, "url").replaceAll("\\D+", ""));
                        comment.setAuthorAvatarUrl(getJsonObject(authorObject, "avatar").getString("large").replaceAll("\\?.*$", ""));
                    } else {
                        comment.setAuthor("此用户不存在");
                    }
                    comment.setContent(getJsonString(jsonObject, "text"));
                    comment.setDate(parseDate(getJsonString(jsonObject, "date_created")));
                    comment.setID(getJsonString(jsonObject, "id"));
                    comment.setHostID(getJsonString(jsonObject, "question_id"));
                    list.add(comment);
                }
                resultObject.ok = true;
                resultObject.result = list;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }

        return resultObject;
    }

    /**
     * 回答问题，使用json请求
     *
     * @param id      问题id
     * @param content 答案内容
     *
     * @return ResultObject.result is the reply_id if ok;
     */
    public static ResultObject<String> answerQuestion(String id, String content) {
        ResultObject<String> resultObject = new ResultObject<>();
        try {
            String url = "http://apis.guokr.com/ask/answer.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("question_id", id);
            pairs.put("content", content);
            String result = HttpFetcher.post(url, pairs).toString();
            JSONObject resultJson = getUniversalJsonObject(result, resultObject);
            if (resultJson != null) {
                String replyID = getJsonString(resultJson, "id");
                resultObject.ok = true;
                resultObject.result = replyID;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 支持答案
     *
     * @param id 答案id
     *
     * @return ResultObject
     */
    public static ResultObject supportAnswer(String id) {
        return supportOrOpposeAnswer(id, "support");
    }

    /**
     * 反对答案
     *
     * @param id 答案id
     *
     * @return ResultObject
     */
    public static ResultObject opposeAnswer(String id) {
        return supportOrOpposeAnswer(id, "oppose");
    }

    /**
     * 支持或者反对答案
     *
     * @param id      答案id
     * @param opinion 反对或者赞同，参数
     *
     * @return ResultObject
     */
    private static ResultObject supportOrOpposeAnswer(String id, String opinion) {
        String url = "http://www.guokr.com/apis/ask/answer_polling.json";
        ResultObject resultObject = new ResultObject();
        try {
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("answer_id", id);
            pairs.put("opinion", opinion);
            String result = HttpFetcher.post(url, pairs).toString();
            if (getUniversalJsonSimpleBoolean(result, resultObject)) {
                resultObject.ok = true;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 感谢答案
     *
     * @param id 答案id
     *
     * @return ResultObject
     */
    public static ResultObject thankAnswer(String id) {
        String url = "http://www.guokr.com/apis/ask/answer_thanking.json";
        ResultObject resultObject = new ResultObject();
        try {
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("v", System.currentTimeMillis() + "");
            pairs.put("answer_id", id);
            String result = HttpFetcher.post(url, pairs).toString();
            if (getUniversalJsonSimpleBoolean(result, resultObject)) {
                resultObject.ok = true;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 不是答案
     *
     * @param id 答案id
     *
     * @return ResultObject
     */
    public static ResultObject buryAnswer(String id) {
        String url = "http://www.guokr.com/apis/ask/answer_burying.json";
        ResultObject resultObject = new ResultObject();
        try {
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("v", System.currentTimeMillis() + "");
            pairs.put("answer_id", id);
            String result = HttpFetcher.post(url, pairs).toString();
            if (getUniversalJsonSimpleBoolean(result, resultObject)) {
                resultObject.ok = true;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 取消不是答案
     *
     * @param id 答案id
     *
     * @return ResultObject
     */
    public static ResultObject unBuryAnswer(String id) {
        String url = "http://www.guokr.com/apis/ask/answer_burying.json";
        ResultObject resultObject = new ResultObject();
        try {
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("answer_id", id);
            String result = HttpFetcher.delete(url, pairs).toString();
            if (getUniversalJsonSimpleBoolean(result, resultObject)) {
                resultObject.ok = true;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 推荐问题
     *
     * @param questionID 问题id
     * @param title      问题标题
     * @param summary    问题summary
     * @param comment    推荐评语
     *
     * @return ResultObject
     */
    public static ResultObject recommendQuestion(String questionID, String title, String summary, String comment) {
        String url = "http://www.guokr.com/question/" + questionID + "/";
        return UserAPI.recommendLink(url, title, summary, comment);
    }

    /**
     * 关注问题
     *
     * @param questionID 问题id
     *
     * @return ResultObject
     */
    public static ResultObject followQuestion(String questionID) {
        ResultObject resultObject = new ResultObject();
        String url = "http://www.guokr.com/apis/ask/question_follower.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("question_id", questionID);
        pairs.put("retrieve_type", "by_question");
        try {
            String result = HttpFetcher.post(url, pairs).toString();
            if (getUniversalJsonSimpleBoolean(result, resultObject)) {
                resultObject.ok = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultObject;
    }

    /**
     * 取消关注问题
     *
     * @param questionID 问题id
     *
     * @return ResultObject
     */
    public static ResultObject unfollowQuestion(String questionID) {
        ResultObject resultObject = new ResultObject();
        String url = "http://www.guokr.com/apis/ask/question_follower.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("question_id", questionID);
        pairs.put("retrieve_type", "by_question");
        try {
            String result = HttpFetcher.delete(url, pairs).toString();
            if (getUniversalJsonSimpleBoolean(result, resultObject)) {
                resultObject.ok = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultObject;
    }


    /**
     * 评论问题
     *
     * @param questionID 问题id
     * @param comment    评论内容
     *
     * @return ResultObject
     */
    public static ResultObject<UComment> commentOnQuestion(String questionID, String comment) {
        String url = "http://www.guokr.com/apis/ask/question_reply.json";
        ResultObject<UComment> resultObject = new ResultObject<>();
        try {
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("question_id", questionID);
            pairs.put("content", comment);
            pairs.put("retrieve_type", "by_question");
            String result = HttpFetcher.post(url, pairs).toString();
            JSONObject jsonObject = getUniversalJsonObject(result, resultObject);
            if (jsonObject != null) {
                UComment uComment = new UComment();
                uComment.setAuthor(jsonObject.getJSONObject("author").getString("nickname"));
                uComment.setAuthorID(jsonObject.getJSONObject("author").getString("url").replaceAll("\\D+", ""));
                uComment.setAuthorAvatarUrl(jsonObject.getJSONObject("author").getJSONObject("avatar").getString("large").replaceAll("\\?.*$", ""));
                uComment.setContent(getJsonString(jsonObject, "text"));
                uComment.setDate(parseDate(getJsonString(jsonObject, "date_created")));
                uComment.setID(getJsonString(jsonObject, "id"));
                uComment.setHostID(getJsonString(jsonObject, "question_id"));
                resultObject.ok = true;
                resultObject.result = uComment;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 删除我的答案
     *
     * @param id 答案id
     *
     * @return ResultObject
     */
    public static ResultObject deleteMyComment(String id) {
        ResultObject resultObject = new ResultObject();
        String url = "http://www.guokr.com/apis/ask/answer/" + id + ".json";
        HashMap<String, String> pairs = new HashMap<>();
        try {
            String result = HttpFetcher.delete(url, pairs).toString();
            resultObject.ok = getUniversalJsonSimpleBoolean(result, resultObject);
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 评论一个答案，resultObject.result 是一个UComment
     *
     * @param answerID 答案id
     * @param comment  评论内容
     *
     * @return ResultObject
     */
    public static ResultObject<UComment> commentOnAnswer(String answerID, String comment) {
        String url = "http://www.guokr.com/apis/ask/answer_reply.json";
        ResultObject<UComment> resultObject = new ResultObject<>();
        try {
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("answer_id", answerID);
            pairs.put("content", comment);
            pairs.put("retrieve_type", "by_answer");
            String result = HttpFetcher.post(url, pairs).toString();
            JSONObject jsonObject = getUniversalJsonObject(result, resultObject);
            if (jsonObject != null) {
                UComment uComment = new UComment();
                uComment.setAuthor(jsonObject.getJSONObject("author").getString("nickname"));
                uComment.setAuthorID(jsonObject.getJSONObject("author").getString("url").replaceAll("\\D+", ""));
                uComment.setAuthorAvatarUrl(jsonObject.getJSONObject("author").getJSONObject("avatar").getString("large").replaceAll("\\?.*$", ""));
                uComment.setContent(getJsonString(jsonObject, "text"));
                uComment.setDate(parseDate(getJsonString(jsonObject, "date_created")));
                uComment.setID(getJsonString(jsonObject, "id"));
                uComment.setHostID(getJsonString(jsonObject, "question_id"));
                resultObject.ok = true;
                resultObject.result = uComment;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 获取提问所需的csrf_token
     * resultObject.result是PrepareData#csrf
     *
     * @return ResultObject
     */
    public static ResultObject<PrepareData> getQuestionPrepareData() {
        ResultObject<PrepareData> resultObject = new ResultObject<>();
        try {
            String url = "http://www.guokr.com/questions/new/";
            String html = HttpFetcher.get(url).toString();
            Document doc = Jsoup.parse(html);
            String csrf = doc.getElementById("csrf_token").attr("value");
            if (!TextUtils.isEmpty(csrf)) {
                PrepareData prepareData = new PrepareData();
                prepareData.setCsrf(csrf);
                resultObject.ok = true;
                resultObject.result = prepareData;
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 提问，卧槽Cookie里面还需要两个值，给跪了_32382_access_token和_32382_ukey=
     * 由https://www.guokr.com/sso/ask/提供，妈蛋先不搞提问了
     *
     * @param csrf       csrf
     * @param question   标题
     * @param annotation 补充
     * @param tags       标签
     *
     * @return ResultObject
     *
     * @deprecated
     */
    public static ResultObject<String> publishQuestion(String csrf, String question, String annotation, String[] tags) {
        ResultObject<String> resultObject = new ResultObject<>();
        String url = "http://www.guokr.com/questions/new/";
        try {
            ResultObject<String> mdResult = MDUtil.parseMarkdownByGitHub(annotation);
            String htmlDesc;
            if (mdResult.ok) {
                htmlDesc = mdResult.result;
            } else {
                htmlDesc = MDUtil.Markdown2HtmlDumb(annotation);
            }
            htmlDesc += Config.getComplexReplyTail();
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("csrf_token", csrf);
            pairs.put("question", question);
            pairs.put("annotation", htmlDesc);
            for (String tag1 : tags) {
                String tag = tag1.trim();
                if (!TextUtils.isEmpty(tag)) {
                    pairs.put("tags", tag);
                }
            }
            pairs.put("captcha", "");

            ResultObject result = HttpFetcher.post(url, pairs, false);
            if (result.statusCode == 302 && testPublishResult(result.toString())) {
                resultObject.ok = true;
                resultObject.result = result.toString();
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 解析页面结果，看看是不是发表成功了
     *
     * @param res 发表问题返回的结果
     *
     * @return 是否成功
     */
    private static boolean testPublishResult(String res) {
        try {
            Document doc = Jsoup.parse(res);
            String href = doc.getElementsByTag("a").attr("href");
            return href.matches("/question/\\d+[/]?");
        } catch (Exception e) {
            return false;
        }
    }

}
