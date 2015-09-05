package net.nashlegend.sourcewall.request.api;

import android.text.TextUtils;

import net.nashlegend.sourcewall.model.AceModel;
import net.nashlegend.sourcewall.model.Article;
import net.nashlegend.sourcewall.model.SubItem;
import net.nashlegend.sourcewall.model.UComment;
import net.nashlegend.sourcewall.request.HttpFetcher;
import net.nashlegend.sourcewall.request.RequestCache;
import net.nashlegend.sourcewall.request.ResultObject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 暂无单个回复地址
 * 缓存key规则：
 * 科学人的key是 article
 * 其余的是 article.{id}
 */
public class ArticleAPI extends APIBase {

    public ArticleAPI() {

    }

    /**
     * 获取《科学人》默认列表，取20个，我发现这样动态请求比果壳首页刷新的快……
     * resultObject.result是ArrayList[Article]
     *
     * @param offset 从第offset个开始取
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Article>> getArticleListIndexPage(int offset) {
        String url = "http://www.guokr.com/apis/minisite/article.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("retrieve_type", "by_subject");
        pairs.put("limit", "20");
        pairs.put("offset", offset + "");
        return getArticleListFromJsonUrl(url, pairs);
    }

    /**
     * 按频道取《科学人》的文章，比如热点、前沿什么的
     * resultObject.result是ArrayList[Article]
     *
     * @param channelKey 频道key
     * @param offset     加载开始的index
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Article>> getArticleListByChannel(String channelKey, int offset) {
        String url = "http://www.guokr.com/apis/minisite/article.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("retrieve_type", "by_channel");
        pairs.put("channel_key", channelKey);
        pairs.put("limit", "20");
        pairs.put("offset", offset + "");
        return getArticleListFromJsonUrl(url, pairs);
    }

    /**
     * 按学科取《科学人》的文章
     * resultObject.result是ArrayList[Article]
     *
     * @param subject_key 学科key
     * @param offset      从第几个开始加载
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Article>> getArticleListBySubject(String subject_key, int offset) {
        String url = "http://www.guokr.com/apis/minisite/article.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("retrieve_type", "by_subject");
        pairs.put("subject_key", subject_key);
        pairs.put("limit", "20");
        pairs.put("offset", offset + "");
        return getArticleListFromJsonUrl(url, pairs);
    }

    /**
     * 根据上面几个方法生成的url去取文章列表
     * resultObject.result是ArrayList[Article]
     *
     * @param url jsonUrl
     *
     * @return ResultObject
     */
    private static ResultObject<ArrayList<Article>> getArticleListFromJsonUrl(String url, HashMap<String, String> pairs) {
        ResultObject<ArrayList<Article>> resultObject = new ResultObject<>();
        try {
            String jString = HttpFetcher.get(url, pairs, false).toString();
            resultObject = parseArticleListJson(jString);
            if (resultObject.ok) {
                //请求成功则缓存之
                String key = null;
                if (pairs.size() == 4 && pairs.get("offset").equals("0")) {
                    String channel_key = pairs.get("channel_key");
                    String subject_key = pairs.get("subject_key");
                    key = "article." + (TextUtils.isEmpty(channel_key) ? subject_key : channel_key);
                } else if (pairs.size() == 3 && pairs.get("offset").equals("0")) {
                    key = "article";
                }
                if (key != null) {
                    RequestCache.getInstance().addStringToCacheForceUpdate(key, jString);
                }
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }


    /**
     * 获取缓存的文章列表
     * resultObject.result是ArrayList[Article]
     *
     * @param subItem SubItem
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Article>> getCachedArticleList(SubItem subItem) {
        ResultObject<ArrayList<Article>> resultObject = new ResultObject<>();
        String key = null;
        if (subItem.getType() == SubItem.Type_Collections) {
            key = "article";
        } else if (subItem.getType() == SubItem.Type_Single_Channel) {
            key = "article." + subItem.getValue();
        } else if (subItem.getType() == SubItem.Type_Subject_Channel) {
            key = "article." + subItem.getValue();
        }
        if (key != null) {
            try {
                String jString = RequestCache.getInstance().getStringFromCache(key);
                if (jString != null) {
                    resultObject = parseArticleListJson(jString);
                }
            } catch (Exception e) {
                handleRequestException(e, resultObject);
            }
        }

        return resultObject;
    }

    /**
     * 获取缓存的文章列表
     * resultObject.result是ArrayList[Article]
     *
     * @param jString 要解析的json
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<Article>> parseArticleListJson(String jString) {
        ResultObject<ArrayList<Article>> resultObject = new ResultObject<>();
        try {
            ArrayList<Article> articleList = new ArrayList<>();
            if (jString != null) {
                JSONArray articles = APIBase.getUniversalJsonArray(jString, resultObject);
                if (articles != null) {
                    for (int i = 0; i < articles.length(); i++) {
                        JSONObject jo = articles.getJSONObject(i);
                        Article article = new Article();
                        article.setId(getJsonString(jo, "id"));
                        article.setCommentNum(getJsonInt(jo, "replies_count"));
                        article.setAuthor(getJsonString(getJsonObject(jo, "author"), "nickname"));
                        article.setAuthorID(getJsonString(getJsonObject(jo, "author"), "url").replaceAll("\\D+", ""));
                        article.setAuthorAvatarUrl(jo.getJSONObject("author").getJSONObject("avatar").getString("large").replaceAll("\\?.*$", ""));
                        article.setDate(parseDate(getJsonString(jo, "date_published")));
                        article.setSubjectName(getJsonString(getJsonObject(jo, "subject"), "name"));
                        article.setSubjectKey(getJsonString(getJsonObject(jo, "subject"), "key"));
                        article.setUrl(getJsonString(jo, "url"));
                        article.setImageUrl(getJsonString(jo, "small_image"));
                        article.setSummary(getJsonString(jo, "summary"));
                        article.setTitle(getJsonString(jo, "title"));
                        articleList.add(article);
                    }
                    resultObject.ok = true;
                    resultObject.result = articleList;
                }
            }
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 根据文章id，解析页面获得文章内容
     * resultObject.result是Article
     *
     * @param id article ID
     *
     * @return ResultObject
     */
    public static ResultObject<Article> getArticleDetailByID(String id) {
        return getArticleDetailByUrl("http://www.guokr.com/article/" + id + "/");
    }

    /**
     * 直接解析页面获得文章内容
     * resultObject.result是Article
     *
     * @param url article页面地址
     */
    public static ResultObject<Article> getArticleDetailByUrl(String url) {
        ResultObject<Article> resultObject = new ResultObject<>();
        try {
            Article article = new Article();
            String aid = url.replaceAll("\\?.*$", "").replaceAll("\\D+", "");
            ResultObject response = HttpFetcher.get(url);
            resultObject.statusCode = response.statusCode;
            if (resultObject.statusCode == 404) {
                return resultObject;
            }
            String html = response.toString();
            Document doc = Jsoup.parse(html);
            //replaceAll("line-height: normal;","");只是简单的处理，以防止Article样式不正确，字体过于紧凑
            //可能还有其他样式没有被我发现，所以加一个 TODO
            String articleContent = doc.getElementById("articleContent").outerHtml().replaceAll("line-height: normal;", "");
            String copyright = doc.getElementsByClass("copyright").outerHtml();
            article.setContent(articleContent + copyright);
            int likeNum = Integer.valueOf(doc.getElementsByClass("recom-num").get(0).text().replaceAll("\\D+", ""));
            // 其他数据已经在列表取得，按理说这里只要合过去就行了，
            // 但是因为有可能从其他地方进入这个页面，所以下面的数据还是要取
            // 但是可以尽量少取，因为很多数据基本已经用不到了
            article.setId(aid);
            Elements infos = doc.getElementsByClass("content-th-info");
            if (infos != null && infos.size() == 1) {
                Element info = infos.get(0);
                Elements infoSubs = info.getElementsByTag("a");//记得见过不是a的
                if (infoSubs != null && infoSubs.size() > 0) {
                    //                    String authorId = info.getElementsByTag("a").attr("href").replaceAll("\\D+", "");
                    String author = info.getElementsByTag("a").text();
                    article.setAuthor(author);
                    //                    article.setAuthorID(authorId);
                }
                Elements meta = info.getElementsByTag("meta");
                if (meta != null && meta.size() > 0) {
                    String date = parseDate(info.getElementsByTag("meta").attr("content"));
                    article.setDate(date);
                }
            }
            //            String num = doc.select(".cmts-title").select(".cmts-hide").get(0).getElementsByClass("gfl").get(0).text().replaceAll("\\D+", "");
            //            article.setCommentNum(Integer.valueOf(num));
            article.setTitle(doc.getElementById("articleTitle").text());
            //            article.setLikeNum(likeNum);
            resultObject.ok = true;
            resultObject.result = article;
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }

        return resultObject;
    }

    /**
     * 解析html获得文章热门评论
     * 暂时先不用ResultObject返回
     *
     * @param hotElement 热门评论元素
     * @param aid        article ID
     *
     * @return ResultObject
     */
    private static ArrayList<UComment> getArticleHotComments(Element hotElement, String aid) throws Exception {
        ArrayList<UComment> list = new ArrayList<>();
        Elements comments = hotElement.getElementsByTag("li");
        if (comments != null && comments.size() > 0) {
            for (int i = 0; i < comments.size(); i++) {
                Element element = comments.get(i);
                UComment comment = new UComment();
                String id = element.id().replace("reply", "");
                Element tmp = element.select(".cmt-img").select(".cmtImg").select(".pt-pic").get(0);

                String authorID = tmp.getElementsByTag("a").get(0).attr("href").replaceAll("\\D+", "");
                String authorAvatarUrl = tmp.getElementsByTag("img").get(0).attr("src").replaceAll("\\?.*$", "");
                String author = tmp.getElementsByTag("a").get(0).attr("title");
                String likeNum = element.getElementsByClass("cmt-do-num").get(0).text();
                String date = element.getElementsByClass("cmt-info").get(0).text();
                String content = element.select(".cmt-content").select(".gbbcode-content").select(".cmtContent").get(0).outerHtml();
                Elements tmpElements = element.getElementsByClass("cmt-auth");
                if (tmpElements != null && tmpElements.size() > 0) {
                    String authorTitle = element.getElementsByClass("cmt-auth").get(0).attr("title");
                    comment.setAuthorTitle(authorTitle);
                }
                comment.setID(id);
                comment.setLikeNum(Integer.valueOf(likeNum));
                comment.setAuthor(author);
                comment.setAuthorID(authorID);
                comment.setAuthorAvatarUrl(authorAvatarUrl);
                comment.setDate(date);
                comment.setContent(content);
                comment.setHostID(aid);
                list.add(comment);
            }
        }
        return list;
    }

    /**
     * 获取文章评论，json格式
     * resultObject.result是ArrayList[UComment]
     *
     * @param id     article ID
     * @param offset 从第几个开始加载
     *
     * @return ResultObject
     */
    public static ResultObject<ArrayList<AceModel>> getArticleComments(String id, int offset, int limit) {
        ResultObject<ArrayList<AceModel>> resultObject = new ResultObject<>();
        try {
            ArrayList<AceModel> list = new ArrayList<>();
            String url = "http://apis.guokr.com/minisite/article_reply.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("article_id", id);
            pairs.put("limit", String.valueOf(limit));
            pairs.put("offset", offset + "");
            String jString = HttpFetcher.get(url, pairs).toString();
            JSONArray articles = APIBase.getUniversalJsonArray(jString, resultObject);
            if (articles != null) {
                for (int i = 0; i < articles.length(); i++) {
                    JSONObject jo = articles.getJSONObject(i);
                    UComment comment = new UComment();
                    comment.setID(getJsonString(jo, "id"));
                    comment.setLikeNum(jo.getInt("likings_count"));
                    comment.setHasLiked(jo.getBoolean("current_user_has_liked"));
                    JSONObject authorObject = getJsonObject(jo, "author");
                    boolean exists = getJsonBoolean(authorObject, "is_exists");
                    comment.setAuthorExists(exists);
                    if (exists) {
                        comment.setAuthor(getJsonString(authorObject, "nickname"));
                        comment.setAuthorTitle(getJsonString(authorObject, "title"));
                        comment.setAuthorID(getJsonString(authorObject, "url").replaceAll("\\D+", ""));
                        comment.setAuthorAvatarUrl(getJsonObject(authorObject, "avatar").getString("large").replaceAll("\\?.*$", ""));
                    } else {
                        comment.setAuthor("此用户不存在");
                    }
                    comment.setDate(parseDate(getJsonString(jo, "date_created")));
                    comment.setFloor((offset + i + 1) + "楼");
                    comment.setContent(getJsonString(jo, "html"));
                    comment.setHostID(id);
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
     * 获取一条article的简介，也就是除了正文之外的一切，这里只需要两个，id和title
     *
     * @param article_id article_id
     *
     * @return ResultObject
     */
    private static ResultObject<Article> getArticleSimpleByID(String article_id) {
        ResultObject<Article> resultObject = new ResultObject<>();
        String url = "http://apis.guokr.com/minisite/article.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("article_id", article_id);
        try {
            Article article = new Article();
            String result = HttpFetcher.get(url, pairs).toString();
            JSONArray articlesArray = getUniversalJsonArray(result, resultObject);
            if (articlesArray != null && articlesArray.length() == 1) {
                JSONObject articleObject = articlesArray.getJSONObject(0);
                String id = getJsonString(articleObject, "id");
                String title = getJsonString(articleObject, "title");
                article.setId(id);
                article.setTitle(title);
                resultObject.ok = true;
                resultObject.result = article;
            }

        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 根据一条通知的id获取所有内容，蛋疼的需要跳转。
     * 先是：http://www.guokr.com/user/notice/8738252/
     * 跳到：http://www.guokr.com/article/reply/123456/
     * 这就走到了类似getSingleCommentFromRedirectUrl这一步
     * 两次跳转后可获得article_id，但是仍然无法获得title
     * 还需要另一个接口获取article的摘要。getArticleSimpleByID(article_id)
     *
     * @param notice_id 通知id
     *
     * @return resultObject resultObject.result是UComment
     */
    public static ResultObject<UComment> getSingleCommentByNoticeID(String notice_id) {
        ResultObject<UComment> resultObject = new ResultObject<>();
        //todo
        String article_id;
        String reply_id;
        if (TextUtils.isEmpty(notice_id)) {
            return resultObject;
        }
        String notice_url = "http://www.guokr.com/user/notice/" + notice_id + "/";
        try {
            ResultObject httpResult = HttpFetcher.get(notice_url);
            String replyRedirectResult = httpResult.toString();
            Document document = Jsoup.parse(replyRedirectResult);
            Elements elements = document.getElementsByTag("a");
            if (elements.size() == 1) {
                Matcher matcher = Pattern.compile("^/article/(\\d+)/.*#reply(\\d+)$").matcher(elements.get(0).text());
                if (matcher.find()) {
                    article_id = matcher.group(1);
                    reply_id = matcher.group(2);
                    ResultObject<Article> articleResult = getArticleSimpleByID(article_id);
                    if (articleResult.ok) {
                        Article article = articleResult.result;
                        return getSingleCommentByID(reply_id, article.getId(), article.getTitle());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultObject;
    }

    /**
     * 根据一条评论的地址获取所有内容，蛋疼的需要跳转
     * http://www.guokr.com/article/reply/123456/
     * 一次跳转后可获得article_id，但是仍然无法获得title
     * 还需要另一个接口获取article的摘要。getArticleSimpleByID(article_id)
     * 多次跳转真让人想死啊。
     *
     * @param reply_url 评论地址
     *
     * @return resultObject resultObject.result是UComment
     */
    public static ResultObject<UComment> getSingleCommentFromRedirectUrl(String reply_url) {
        ResultObject<UComment> resultObject = new ResultObject<>();
        //todo
        String article_id;
        String reply_id;
        try {
            reply_id = reply_url.replaceAll("\\D+", "");
            ResultObject httpResult = HttpFetcher.get(reply_url);
            String replyRedirectResult = httpResult.toString();
            Document document = Jsoup.parse(replyRedirectResult);
            Elements elements = document.getElementsByTag("a");
            if (elements.size() == 1) {
                Matcher matcher = Pattern.compile("^/article/(\\d+)/.*#reply(\\d+)$").matcher(elements.get(0).text());
                if (matcher.find()) {
                    article_id = matcher.group(1);
                    ResultObject<Article> articleResult = getArticleSimpleByID(article_id);
                    if (articleResult.ok) {
                        Article article = articleResult.result;
                        return getSingleCommentByID(reply_id, article.getId(), article.getTitle());
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultObject;
    }

    /**
     * 根据一条评论的id获取评论内容，主要应用于消息通知
     * 无法取得此评论的文章的id和标题，无法取得楼层。
     * 太蛋疼了，只能不显示文章标题或者提前传入
     *
     * @param reply_id 评论id
     *
     * @return resultObject resultObject.result是UComment
     */
    public static ResultObject<UComment> getSingleCommentByID(String reply_id, String article_id, String article_title) {
        ResultObject<UComment> resultObject = new ResultObject<>();
        String url = "http://apis.guokr.com/minisite/article_reply.json";
        //url还有另一种形式，http://apis.guokr.com/minisite/article_reply/99999999.json;
        //这样后面就不必带reply_id参数了
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("reply_id", reply_id);
        try {
            UComment comment = new UComment();

            String result = HttpFetcher.get(url, pairs).toString();
            JSONObject replyObject = getUniversalJsonObject(result, resultObject);
            boolean hasLiked = getJsonBoolean(replyObject, "current_user_has_liked");
            String date = parseDate(getJsonString(replyObject, "date_created"));
            int likeNum = getJsonInt(replyObject, "likings_count");
            String content = getJsonString(replyObject, "html");
            JSONObject authorObject = getJsonObject(replyObject, "author");
            boolean is_exists = getJsonBoolean(authorObject, "is_exists");

            if (is_exists) {
                String author = getJsonString(authorObject, "nickname");
                String authorID = getJsonString(authorObject, "url").replaceAll("\\D+", "");
                String authorTitle = getJsonString(authorObject, "title");
                JSONObject avatarObject = getJsonObject(authorObject, "avatar");
                String avatarUrl = getJsonString(avatarObject, "large").replaceAll("\\?.*$", "");
                comment.setAuthor(author);
                comment.setAuthorTitle(authorTitle);
                comment.setAuthorID(authorID);
                comment.setAuthorAvatarUrl(avatarUrl);
            } else {
                comment.setAuthor("此用户不存在");
            }

            comment.setHostID(article_id);
            comment.setHostTitle(article_title);
            comment.setDate(date);
            comment.setHasLiked(hasLiked);
            comment.setLikeNum(likeNum);
            comment.setContent(content);
            comment.setID(reply_id);
            resultObject.ok = true;
            resultObject.result = comment;
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }


    /**
     * 推荐文章
     *
     * @param articleID article ID
     * @param title     文章标题
     * @param summary   文章总结
     * @param comment   推荐评语
     *
     * @return ResultObject
     */
    public static ResultObject recommendArticle(String articleID, String title, String summary, String comment) {
        String articleUrl = "http://www.guokr.com/article/" + articleID + "/";
        return UserAPI.recommendLink(articleUrl, title, summary, comment);
    }

    /**
     * 赞一个文章评论
     *
     * @param id 文章id
     *
     * @return ResultObject
     */
    public static ResultObject likeComment(String id) {
        String url = "http://www.guokr.com/apis/minisite/article_reply_liking.json";
        ResultObject resultObject = new ResultObject();
        try {
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("reply_id", id);
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
     * 删除我的评论
     *
     * @param id 评论id
     *
     * @return ResultObject
     */
    public static ResultObject deleteMyComment(String id) {
        ResultObject resultObject = new ResultObject();
        String url = "http://www.guokr.com/apis/minisite/article_reply.json";
        HashMap<String, String> pairs = new HashMap<>();
        pairs.put("reply_id", id);
        try {
            String result = HttpFetcher.delete(url, pairs).toString();
            resultObject.ok = getUniversalJsonSimpleBoolean(result, resultObject);
        } catch (Exception e) {
            handleRequestException(e, resultObject);
        }
        return resultObject;
    }

    /**
     * 使用json请求回复文章
     *
     * @param id      文章id
     * @param content 回复内容
     *
     * @return ResultObject.result is the reply_id if ok;
     */
    public static ResultObject replyArticle(String id, String content) {
        ResultObject resultObject = new ResultObject();
        try {
            String url = "http://apis.guokr.com/minisite/article_reply.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("article_id", id);
            pairs.put("content", content);
            String result = HttpFetcher.post(url, pairs).toString();
            JSONObject resultJson = APIBase.getUniversalJsonObject(result, resultObject);
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
     * 使用网页请求而不是json来获得结果，可以使用高级样式
     *
     * @param id      文章id
     * @param content 回复内容，html格式
     *
     * @return ResultObject.result is the reply_id if ok;
     */
    public static ResultObject replyArticleAdvanced(String id, String content) {
        ResultObject resultObject = new ResultObject();
        try {
            String url = "http://apis.guokr.com/minisite/article_reply.json";
            HashMap<String, String> pairs = new HashMap<>();
            pairs.put("article_id", id);
            pairs.put("content", content);
            String result = HttpFetcher.post(url, pairs).toString();
            JSONObject resultJson = APIBase.getUniversalJsonObject(result, resultObject);
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
}
