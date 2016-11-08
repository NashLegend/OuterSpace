package net.nashlegend.sourcewall.data.database;

import net.nashlegend.sourcewall.data.Consts.Keys;
import net.nashlegend.sourcewall.data.database.gen.AskTag;
import net.nashlegend.sourcewall.data.database.gen.AskTagDao;
import net.nashlegend.sourcewall.model.SubItem;
import net.nashlegend.sourcewall.util.ErrorUtils;
import net.nashlegend.sourcewall.util.PrefsUtil;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;

/**
 * Created by NashLegend on 2015/1/20 0020
 */
public class AskTagHelper {

    public static long getAskTagsNumber() {
        AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
        return tagDao.count();
    }

    public static List<AskTag> getAllMyTags() {
        AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
        QueryBuilder<AskTag> builder = tagDao.queryBuilder().
                orderAsc(AskTagDao.Properties.Order);
        List<AskTag> list = builder.list();
        return list;
    }

    public static List<SubItem> getAllMyTagSubItems() {
        AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
        QueryBuilder<AskTag> builder = tagDao.queryBuilder().
                orderAsc(AskTagDao.Properties.Order);
        List<AskTag> askTags = builder.list();
        ArrayList<SubItem> subItems = new ArrayList<>();
        for (int i = 0; i < askTags.size(); i++) {
            AskTag tag = askTags.get(i);
            SubItem subItem = new SubItem(tag.getSection(), tag.getType(), tag.getName(),
                    tag.getValue());
            subItems.add(subItem);
        }
        return subItems;
    }

    public static List<AskTag> getSelectedTags() {
        AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
        QueryBuilder<AskTag> builder = tagDao.queryBuilder().where(
                AskTagDao.Properties.Selected.eq(true)).
                orderAsc(AskTagDao.Properties.Order);
        return builder.list();
    }

    public static List<SubItem> getSelectedQuestionSubItems() {
        AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
        QueryBuilder<AskTag> builder = tagDao.queryBuilder().where(
                AskTagDao.Properties.Selected.eq(true)).
                orderAsc(AskTagDao.Properties.Order);
        List<AskTag> askTags = builder.list();
        ArrayList<SubItem> subItems = new ArrayList<>();
        for (int i = 0; i < askTags.size(); i++) {
            AskTag tag = askTags.get(i);
            SubItem subItem = new SubItem(tag.getSection(), tag.getType(), tag.getName(),
                    tag.getValue());
            subItems.add(subItem);
        }
        return subItems;
    }

    public static List<AskTag> getUnselectedTags() {
        AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
        QueryBuilder<AskTag> builder = tagDao.queryBuilder().
                where(AskTagDao.Properties.Selected.eq(false)).
                orderAsc(AskTagDao.Properties.Order);
        return builder.list();
    }

    public static void putAllMyTags(List<AskTag> myTags) {
        BaseDB.getDaoSession().getDatabase().beginTransaction();
        try {
            AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
            tagDao.deleteAll();
            tagDao.insertInTx(myTags);
            BaseDB.getDaoSession().getDatabase().setTransactionSuccessful();
            PrefsUtil.saveLong(Keys.Key_Last_Ask_Tags_Version, System.currentTimeMillis());
        } catch (Exception e) {
            ErrorUtils.onException(e);
        } finally {
            BaseDB.getDaoSession().getDatabase().endTransaction();
        }
    }

    /**
     * 调整未选中项的顺序，Key_Last_Ask_Tags_Version
     *
     * @param myTags tag
     */
    public static void putUnselectedTags(List<AskTag> myTags) {
        BaseDB.getDaoSession().getDatabase().beginTransaction();
        try {
            List<AskTag> tags = getSelectedTags();
            tags.addAll(myTags);
            for (int i = 0; i < tags.size(); i++) {
                tags.get(i).setId(null);
            }
            AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
            tagDao.deleteAll();
            tagDao.insertInTx(tags);
            BaseDB.getDaoSession().getDatabase().setTransactionSuccessful();
        } catch (Exception e) {
            ErrorUtils.onException(e);
        } finally {
            BaseDB.getDaoSession().getDatabase().endTransaction();
        }
    }

    public static void clearAllMyTags() {
        AskTagDao tagDao = BaseDB.getDaoSession().getAskTagDao();
        tagDao.deleteAll();
        PrefsUtil.saveLong(Keys.Key_Last_Ask_Tags_Version, System.currentTimeMillis());
    }

}
