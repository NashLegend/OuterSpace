package net.nashlegend.sourcewall.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ProgressBar;

import net.nashlegend.sourcewall.AppApplication;
import net.nashlegend.sourcewall.ArticleActivity;
import net.nashlegend.sourcewall.MainActivity;
import net.nashlegend.sourcewall.R;
import net.nashlegend.sourcewall.adapters.ArticleAdapter;
import net.nashlegend.sourcewall.commonview.AAsyncTask;
import net.nashlegend.sourcewall.commonview.IStackedAsyncTaskInterface;
import net.nashlegend.sourcewall.commonview.LListView;
import net.nashlegend.sourcewall.commonview.LoadingView;
import net.nashlegend.sourcewall.model.Article;
import net.nashlegend.sourcewall.model.SubItem;
import net.nashlegend.sourcewall.request.ResultObject;
import net.nashlegend.sourcewall.request.api.ArticleAPI;
import net.nashlegend.sourcewall.util.Consts;
import net.nashlegend.sourcewall.util.SharedPreferencesUtil;
import net.nashlegend.sourcewall.view.ArticleListItemView;

import java.util.ArrayList;

/**
 * Created by NashLegend on 2014/9/18 0018
 */
public class ArticlesFragment extends ChannelsFragment implements LListView.OnRefreshListener, LoadingView.ReloadListener {

    private LListView listView;
    private ArticleAdapter adapter;
    private LoaderTask task;
    private SubItem subItem;
    private LoadingView loadingView;
    private final long cacheDuration = 300;//5分钟内连续进入，则不更新
    private ProgressBar progressBar;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public View onCreateLayoutView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_articles, container, false);
        loadingView = (LoadingView) view.findViewById(R.id.article_progress_loading);
        loadingView.setReloadListener(this);
        subItem = (SubItem) getArguments().getSerializable(Consts.Extra_SubItem);
        listView = (LListView) view.findViewById(R.id.list_articles);
        adapter = new ArticleAdapter(getActivity());
        listView.setCanPullToRefresh(false);
        listView.setCanPullToLoadMore(false);
        listView.setAdapter(adapter);
        listView.setOnRefreshListener(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (view instanceof ArticleListItemView) {
                    Intent intent = new Intent();
                    intent.setClass(AppApplication.getApplication(), ArticleActivity.class);
                    intent.putExtra(Consts.Extra_Article, ((ArticleListItemView) view).getData());
                    startActivity(intent);
                    getActivity().overridePendingTransition(R.anim.slide_in_right, 0);
                }

            }
        });
        progressBar = (ProgressBar) view.findViewById(R.id.articles_loading);
        setTitle();
        loadOver();
        return view;
    }

    @Override
    public void onCreateViewAgain(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        SubItem mSubItem = (SubItem) getArguments().getSerializable(Consts.Extra_SubItem);
        resetData(mSubItem);
    }

    @Override
    public void setTitle() {
        if (subItem.getType() == SubItem.Type_Collections) {
            getActivity().setTitle("科学人");
            ((MainActivity) getActivity()).getSupportActionBar().setTitle("科学人");
        } else {
            getActivity().setTitle(this.subItem.getName() + " -- 科学人");
            ((MainActivity) getActivity()).getSupportActionBar().setTitle(this.subItem.getName() + " -- 科学人");
        }
    }

    private void loadOver() {
        loadData(0);
        loadingView.startLoading();
    }

    private void loadData(int offset) {
        cancelPotentialTask();
        task = new LoaderTask(this);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, offset);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onStartRefresh() {
        loadData(0);
    }

    @Override
    public void onStartLoadMore() {
        loadData(adapter.getCount());
    }

    @Override
    public int getFragmentMenu() {
        return R.menu.menu_fragment_article;
    }

    @Override
    public boolean takeOverMenuInflate(MenuInflater inflater, Menu menu) {
        inflater.inflate(getFragmentMenu(), menu);
        return true;
    }

    @Override
    public boolean takeOverOptionsItemSelect(MenuItem item) {
        return true;
    }

    @Override
    public boolean takeOverBackPressed() {
        return false;
    }

    @Override
    public void resetData(SubItem subItem) {
        if (subItem.equals(this.subItem)) {
            loadingView.onLoadSuccess();
            if (adapter == null || adapter.getCount() == 0) {
                triggerRefresh();
            }
        } else {
            this.subItem = subItem;
            adapter.clear();
            adapter.notifyDataSetInvalidated();
            listView.setCanPullToRefresh(false);
            listView.setCanPullToLoadMore(false);
            loadOver();
        }
        setTitle();
    }

    @Override
    public void triggerRefresh() {
        listView.startRefreshing();
    }

    @Override
    public void prepareLoading(SubItem sub) {
        if (sub == null || !sub.equals(this.subItem)) {
            loadingView.startLoading();
        }
    }

    @Override
    public void scrollToHead() {
        listView.setSelection(0);
    }

    private void cancelPotentialTask() {
        if (task != null && task.getStatus() == AAsyncTask.Status.RUNNING) {
            task.cancel(true);
            listView.doneOperation();
        }
    }

    @Override
    public void reload() {
        loadData(0);
    }

    class LoaderTask extends AAsyncTask<Integer, ResultObject<ArrayList<Article>>, ResultObject<ArrayList<Article>>> {

        int offset;

        LoaderTask(IStackedAsyncTaskInterface iStackedAsyncTaskInterface) {
            super(iStackedAsyncTaskInterface);
        }

        @Override
        protected ResultObject<ArrayList<Article>> doInBackground(Integer... datas) {
            offset = datas[0];
            String key = String.valueOf(subItem.getSection()) + subItem.getType() + subItem.getName() + subItem.getValue();

            if (offset == 0 && adapter.getCount() == 0) {
                ResultObject<ArrayList<Article>> cachedResultObject = ArticleAPI.getCachedArticleList(subItem);
                if (cachedResultObject.ok) {
                    long lastLoad = SharedPreferencesUtil.readLong(key, 0l) / 1000;
                    long crtLoad = System.currentTimeMillis() / 1000;
                    if (crtLoad - lastLoad > cacheDuration) {
                        publishProgress(cachedResultObject);
                    } else {
                        return cachedResultObject;
                    }
                }
            }

            ResultObject<ArrayList<Article>> resultObject = new ResultObject<>();
            if (subItem.getType() == SubItem.Type_Collections) {
                resultObject = ArticleAPI.getArticleListIndexPage(offset);
            } else if (subItem.getType() == SubItem.Type_Single_Channel) {
                resultObject = ArticleAPI.getArticleListByChannel(subItem.getValue(), offset);
            } else if (subItem.getType() == SubItem.Type_Subject_Channel) {
                resultObject = ArticleAPI.getArticleListBySubject(subItem.getValue(), offset);
            }

            if (resultObject.ok) {
                SharedPreferencesUtil.saveLong(key, System.currentTimeMillis());
            }

            return resultObject;
        }

        /**
         * 加载缓存的列表，但是会导致点击的时候会更明显的卡一下
         *
         * @param values The values indicating progress.
         */
        @Override
        protected void onProgressUpdate(ResultObject<ArrayList<Article>>... values) {
            ResultObject<ArrayList<Article>> result = values[0];
            ArrayList<Article> ars = result.result;
            if (ars.size() > 0) {
                progressBar.setVisibility(View.VISIBLE);
                loadingView.onLoadSuccess();
                adapter.setList(ars);
                adapter.notifyDataSetInvalidated();
            }
        }

        @Override
        protected void onPostExecute(ResultObject<ArrayList<Article>> result) {
            listView.doneOperation();
            progressBar.setVisibility(View.GONE);
            if (result.ok) {
                loadingView.onLoadSuccess();
                ArrayList<Article> ars = result.result;
                if (offset > 0) {
                    if (ars.size() > 0) {
                        adapter.addAll(ars);
                        adapter.notifyDataSetChanged();
                    }
                } else {
                    if (ars.size() > 0) {
                        adapter.setList(ars);
                        adapter.notifyDataSetChanged();
                    }
                }
            } else {
                toast(R.string.load_failed);
                loadingView.onLoadFailed();
            }
            if (adapter.getCount() > 0) {
                listView.setCanPullToLoadMore(true);
                listView.setCanPullToRefresh(true);
            } else {
                listView.setCanPullToLoadMore(false);
                listView.setCanPullToRefresh(true);
            }
        }

        @Override
        public void onCancel() {
            loadingView.onLoadSuccess();
            if (adapter.getCount() > 0) {
                listView.setCanPullToLoadMore(true);
                listView.setCanPullToRefresh(true);
            } else {
                listView.setCanPullToLoadMore(false);
                listView.setCanPullToRefresh(true);
            }
            listView.doneOperation();
        }
    }

}
