package net.nashlegend.sourcewall.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.umeng.analytics.MobclickAgent;

import net.nashlegend.sourcewall.R;
import net.nashlegend.sourcewall.commonview.AAsyncTask;
import net.nashlegend.sourcewall.commonview.IStackedAsyncTaskInterface;
import net.nashlegend.sourcewall.dialogs.InputDialog;
import net.nashlegend.sourcewall.model.AceModel;
import net.nashlegend.sourcewall.model.Article;
import net.nashlegend.sourcewall.model.Post;
import net.nashlegend.sourcewall.model.Question;
import net.nashlegend.sourcewall.model.UComment;
import net.nashlegend.sourcewall.request.api.APIBase;
import net.nashlegend.sourcewall.swrequest.RequestObject;
import net.nashlegend.sourcewall.swrequest.ResponseObject;
import net.nashlegend.sourcewall.util.CommonUtil;
import net.nashlegend.sourcewall.util.Consts;
import net.nashlegend.sourcewall.util.FileUtil;
import net.nashlegend.sourcewall.util.Mob;
import net.nashlegend.sourcewall.util.RegUtil;
import net.nashlegend.sourcewall.util.SharedPreferencesUtil;
import net.nashlegend.sourcewall.util.SketchSharedUtil;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 格式使用的应该是UBB代码
 */
public class ReplyActivity extends SwipeActivity implements View.OnClickListener {

    private EditText editText;
    private TextView hostText;
    private AceModel aceModel;
    private ImageButton imgButton;
    private ImageButton insertButton;
    private ImageButton publishButton;
    private View uploadingProgress;
    private ProgressDialog progressDialog;
    private String tmpImagePath;
    private UComment comment;
    private boolean replyOK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reply);
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setSupportActionBar(toolbar);
        aceModel = getIntent().getParcelableExtra(Consts.Extra_Ace_Model);
        comment = getIntent().getParcelableExtra(Consts.Extra_Simple_Comment);
        editText = (EditText) findViewById(R.id.text_reply);
        hostText = (TextView) findViewById(R.id.text_reply_host);
        if (comment != null) {
            hostText.setVisibility(View.VISIBLE);
            String cont = RegUtil.html2PlainTextWithoutBlockQuote(comment.getContent());
            if (cont.length() > 100) {
                cont = cont.substring(0, 100) + "...";
            }
            hostText.setText(String.format("引用@%s 的话：%s", comment.getAuthor().getName(), cont));
        }
        if (aceModel instanceof Question) {
            setTitle("回答问题");
            editText.setHint(R.string.hint_answer);
        }
        publishButton = (ImageButton) findViewById(R.id.btn_publish);
        imgButton = (ImageButton) findViewById(R.id.btn_add_img);
        insertButton = (ImageButton) findViewById(R.id.btn_insert_img);
        ImageButton linkButton = (ImageButton) findViewById(R.id.btn_link);
        uploadingProgress = findViewById(R.id.prg_uploading_img);
        publishButton.setOnClickListener(this);
        imgButton.setOnClickListener(this);
        insertButton.setOnClickListener(this);
        linkButton.setOnClickListener(this);
        tryRestoreReply();
    }

    private void invokeImageDialog() {
        String[] ways = {getString(R.string.add_image_from_disk), getString(R.string.add_image_from_camera), getString(R.string.add_image_from_link)};
        new AlertDialog.Builder(this).setTitle(R.string.way_to_add_image).setItems(ways, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        Intent intent = new Intent();
                        intent.setType("image/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(intent, Consts.Code_Invoke_Image_Selector);
                        break;
                    case 1:
                        invokeCamera();
                        break;
                    case 2:
                        invokeImageUrlDialog();
                        break;
                }
            }
        }).create().show();
    }

    private String getPossibleUrlFromClipBoard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = manager.getPrimaryClip();
        String chars = "";
        if (clip != null && clip.getItemCount() > 0) {
            String tmpChars = (clip.getItemAt(0).coerceToText(this).toString()).trim();
            if (tmpChars.startsWith("http://") || tmpChars.startsWith("https://")) {
                chars = tmpChars;
            }
        }
        return chars;
    }

    private void invokeImageUrlDialog() {
        InputDialog.Builder builder = new InputDialog.Builder(this);
        builder.setTitle(R.string.input_image_url);
        builder.setCancelable(true);
        builder.setCanceledOnTouchOutside(false);
        builder.setSingleLine();
        builder.setInputText(getPossibleUrlFromClipBoard());
        builder.setOnClickListener(new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    InputDialog d = (InputDialog) dialog;
                    String text = d.InputString;
                    insertImagePath(text.trim());
                }
            }
        });
        InputDialog inputDialog = builder.create();
        inputDialog.show();
    }

    public void uploadImage(String path) {
        if (FileUtil.isImage(path)) {
            if (new File(path).isFile()) {
                ImageUploadTask task = new ImageUploadTask(this);
                task.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR, path);
            } else {
                toast(R.string.file_not_exists);
            }
        } else {
            toast(R.string.file_not_image);
        }
    }

    private void doneUploadingImage(String url) {
        tmpImagePath = url;
        setImageButtonsPrepared();
    }

    /**
     * 插入图片
     */
    private void insertImagePath(String url) {
        String imgTag = "[image]" + url + "[/image]";
        SpannableString spanned = new SpannableString(imgTag);
        Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_text_image);
        String displayed = "图片链接...";
        ImageSpan imageSpan = getImageSpan(displayed, sourceBitmap);
        spanned.setSpan(imageSpan, 0, imgTag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int start = editText.getSelectionStart();
        editText.getText().insert(start, " ").insert(start + 1, spanned).insert(start + 1 + imgTag.length(), " ");
        resetImageButtons();
    }

    File tmpUploadFile = null;

    private void invokeCamera() {
        String parentPath;
        File pFile = null;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            pFile = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }
        if (pFile == null) {
            pFile = getFilesDir();
        }
        parentPath = pFile.getAbsolutePath();
        tmpUploadFile = new File(parentPath, System.currentTimeMillis() + ".jpg");
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri localUri = Uri.fromFile(tmpUploadFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, localUri);
        startActivityForResult(intent, Consts.Code_Invoke_Camera);
    }

    /**
     * 插入链接
     */
    private void insertLink() {
        InputDialog.Builder builder = new InputDialog.Builder(this);
        builder.setTitle(R.string.input_link_url);
        builder.setCancelable(true);
        builder.setCanceledOnTouchOutside(false);
        builder.setTwoLine();
        builder.setInputText(getPossibleUrlFromClipBoard());
        builder.setOnClickListener(new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    InputDialog d = (InputDialog) dialog;
                    String url = d.InputString;
                    if (!url.startsWith("http")) {
                        url = "http://" + url;
                    }
                    String title = d.InputString2;
                    String result = "[url=" + url + "]" + title + "[/url]";
                    SpannableString spanned = new SpannableString(result);
                    Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.link_gray);
                    String displayed;
                    if (TextUtils.isEmpty(title.trim())) {
                        Uri uri = Uri.parse(url);
                        displayed = uri.getHost();
                        if (TextUtils.isEmpty(displayed)) {
                            displayed = "网络地址";
                        }
                        displayed += "...";
                    } else {
                        displayed = title;
                    }
                    ImageSpan imageSpan = getImageSpan(displayed, sourceBitmap);
                    spanned.setSpan(imageSpan, 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int start = editText.getSelectionStart();
                    editText.getText().insert(start, " ").insert(start + 1, spanned).insert(start + 1 + result.length(), " ");
                }
            }
        });
        InputDialog inputDialog = builder.create();
        inputDialog.show();
    }

    private void publishReply(String rep) {
        if (aceModel instanceof Article) {
            MobclickAgent.onEvent(ReplyActivity.this, Mob.Event_Reply_Article);
        } else if (aceModel instanceof Post) {
            MobclickAgent.onEvent(ReplyActivity.this, Mob.Event_Reply_Post);
        } else if (aceModel instanceof Question) {
            MobclickAgent.onEvent(ReplyActivity.this, Mob.Event_Answer_Question);
        }
        String header = "";
        if (comment != null) {
            header = "[blockquote]" + hostText.getText() + "[/blockquote]";
        }
        final RequestObject<String> requestObject = APIBase.reply(aceModel, header + rep, new RequestObject.CallBack<String>() {
            @Override
            public void onFailure(@Nullable Throwable e, @NonNull ResponseObject<String> result) {
                CommonUtil.dismissDialog(progressDialog);
                toast(R.string.reply_failed);
            }

            @Override
            public void onResponse(@NonNull ResponseObject<String> result) {
                CommonUtil.dismissDialog(progressDialog);
                if (result.ok) {
                    toast(R.string.reply_ok);
                    setResult(RESULT_OK);
                    replyOK = true;
                    tryClearSketch();
                    finish();
                } else {
                    toast(R.string.reply_failed);
                }
            }
        });
        progressDialog = new ProgressDialog(ReplyActivity.this);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setMessage(getString(R.string.message_wait_a_minute));
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (requestObject != null) {
                    requestObject.softCancel();
                }
            }
        });
        progressDialog.show();
    }

    private void hideInput() {
        try {
            if (getCurrentFocus() != null) {
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_publish:
                if (!TextUtils.isEmpty(editText.getText().toString().trim())) {
                    hideInput();
                    publishReply(editText.getText().toString());
                } else {
                    toast(R.string.content_cannot_be_empty);
                }
                break;
            case R.id.btn_add_img:
                invokeImageDialog();
                break;
            case R.id.btn_insert_img:
                insertImagePath(tmpImagePath);
                break;
            case R.id.btn_link:
                insertLink();
                break;
        }
    }

    private void tryRestoreReply() {
        String content = "";
        if (aceModel != null) {
            if (aceModel instanceof Article) {
                content = SketchSharedUtil.readString(Consts.Key_Sketch_Article_Reply + "_" + ((Article) aceModel).getId(), "");
            } else if (aceModel instanceof Post) {
                content = SketchSharedUtil.readString(Consts.Key_Sketch_Post_Reply + "_" + ((Post) aceModel).getId(), "");
            } else if (aceModel instanceof Question) {
                content = SketchSharedUtil.readString(Consts.Key_Sketch_Question_Answer + "_" + ((Question) aceModel).getId(), "");
            }
        }
        editText.setText(restore2Spanned(content));
    }

    public SpannableString restore2Spanned(String str) {
        SpannableString spanned = new SpannableString(str);
        String regImageAndLinkString = "(\\[image\\]([^\\[\\]]+)\\[/image\\])|(\\[url=([^\\[\\]]*)\\]([^\\[\\]]*)\\[/url\\])";
        Matcher matcher = Pattern.compile(regImageAndLinkString).matcher(str);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            //matcher.groupCount()==5;所以最多可以matcher.group(5)
            //matcher.group(0)表示匹配到的字符串;可能是图片链接字符串

            //matcher.group(1)表示匹配到的图片链接字符串;
            //matcher.group(2)表示匹配到的图片链接;

            //matcher.group(3)表示匹配到的超链接字符串;
            //matcher.group(4)表示匹配到的超链接地址字符串;
            //matcher.group(5)表示匹配到的超链接标题字符串;
            //4和5与PublishPostActivity的顺序相反
            if (!TextUtils.isEmpty(matcher.group(1))) {
                //String imageUrl = matcher.group(2);
                Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_text_image);
                ImageSpan imageSpan = getImageSpan("图片链接...", sourceBitmap);
                spanned.setSpan(imageSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                String linkUrl = matcher.group(4);
                String linkTitle = matcher.group(5);
                if (!linkUrl.startsWith("http")) {
                    linkUrl = "http://" + linkUrl;
                }
                Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.link_gray);
                String displayed;
                if (TextUtils.isEmpty(linkTitle.trim())) {
                    Uri uri = Uri.parse(linkUrl);
                    displayed = uri.getHost();
                    if (TextUtils.isEmpty(displayed)) {
                        displayed = "网络地址";
                    }
                    displayed += "...";
                } else {
                    displayed = linkTitle;
                }
                ImageSpan imageSpan = getImageSpan(displayed, sourceBitmap);
                spanned.setSpan(imageSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return spanned;
    }

    private ImageSpan getImageSpan(String displayed, Bitmap sourceBitmap) {

        int size = (int) editText.getTextSize();
        int height = editText.getLineHeight();

        //根据要绘制的文字计算bitmap的宽度
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLUE);
        textPaint.setTextSize(size);
        float textFrom = (float) (size * 1.2);
        float textEndSpan = (float) (size * 0.3);
        float[] widths = new float[displayed.length()];
        textPaint.getTextWidths(displayed, 0, displayed.length(), widths);
        float totalWidth = 0;
        for (float width : widths) {
            totalWidth += width;
        }

        //生成对应尺寸的bitmap
        Bitmap bitmap = Bitmap.createBitmap((int) (totalWidth + textFrom + textEndSpan), height, Bitmap.Config.ARGB_8888);

        //缩放sourceBitmap
        Matrix matrix = new Matrix();
        float scale = size / sourceBitmap.getWidth();
        matrix.setScale(scale, scale);
        matrix.postTranslate((height - size) / 2, (height - size) / 2);

        Canvas canvas = new Canvas(bitmap);

        //画背景
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#009699"));
        canvas.drawRect(0f, 0f, bitmap.getWidth(), bitmap.getHeight(), bgPaint);

        //画图标
        Paint paint = new Paint();
        canvas.drawBitmap(sourceBitmap, matrix, paint);

        //画文字
        canvas.drawText(displayed, textFrom, -textPaint.getFontMetrics().ascent, textPaint);

        return new ImageSpan(this, bitmap, ImageSpan.ALIGN_BOTTOM);
    }

    private void tryClearSketch() {
        if (aceModel instanceof Article) {
            SketchSharedUtil.remove(Consts.Key_Sketch_Article_Reply + "_" + ((Article) aceModel).getId());
        } else if (aceModel instanceof Post) {
            SketchSharedUtil.remove(Consts.Key_Sketch_Post_Reply + "_" + ((Post) aceModel).getId());
        } else if (aceModel instanceof Question) {
            SketchSharedUtil.remove(Consts.Key_Sketch_Question_Answer + "_" + ((Question) aceModel).getId());
        }
    }

    private void saveSketch() {
        if (!replyOK && !TextUtils.isEmpty(editText.getText().toString().trim()) && aceModel != null) {
            String sketch = editText.getText().toString();
            if (aceModel instanceof Article) {
                SketchSharedUtil.saveString(Consts.Key_Sketch_Article_Reply + "_" + ((Article) aceModel).getId(), sketch);
            } else if (aceModel instanceof Post) {
                SketchSharedUtil.saveString(Consts.Key_Sketch_Post_Reply + "_" + ((Post) aceModel).getId(), sketch);
            } else if (aceModel instanceof Question) {
                SketchSharedUtil.saveString(Consts.Key_Sketch_Question_Answer + "_" + ((Question) aceModel).getId(), sketch);
            }
        } else if (!replyOK && TextUtils.isEmpty(editText.getText().toString().trim())) {
            tryClearSketch();
        }
    }

    @Override
    protected void onDestroy() {
        saveSketch();
        super.onDestroy();
    }

    private void resetImageButtons() {
        tmpImagePath = "";
        insertButton.setVisibility(View.GONE);
        imgButton.setVisibility(View.VISIBLE);
        uploadingProgress.setVisibility(View.GONE);
    }

    private void setImageButtonsUploading() {
        insertButton.setVisibility(View.GONE);
        imgButton.setVisibility(View.GONE);
        uploadingProgress.setVisibility(View.VISIBLE);
    }

    private void setImageButtonsPrepared() {
        insertButton.setVisibility(View.VISIBLE);
        imgButton.setVisibility(View.GONE);
        uploadingProgress.setVisibility(View.GONE);
    }

    class ImageUploadTask extends AAsyncTask<String, Integer, ResponseObject<String>> {

        ImageUploadTask(IStackedAsyncTaskInterface iStackedAsyncTaskInterface) {
            super(iStackedAsyncTaskInterface);
        }

        @Override
        protected void onPreExecute() {
            if (!SharedPreferencesUtil.readBoolean(Consts.Key_User_Has_Learned_Add_Image, false)) {
                new AlertDialog.Builder(ReplyActivity.this).setTitle(R.string.hint).setMessage(R.string.tip_of_user_learn_add_image).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.saveBoolean(Consts.Key_User_Has_Learned_Add_Image, true);
                    }
                }).create().show();
            }
            setImageButtonsUploading();
        }

        @Override
        protected ResponseObject<String> doInBackground(String... params) {
            String path = params[0];
            return APIBase.uploadImage(path, true);
        }

        @Override
        protected void onPostExecute(ResponseObject<String> result) {
            if (result.ok) {
                // tap to insert image
                toast(getString(R.string.hint_click_to_add_image_to_editor));
                doneUploadingImage(result.result);
                if (tmpUploadFile != null && tmpUploadFile.exists()) {
                    tmpUploadFile.delete();
                }
            } else {
                resetImageButtons();
                toast(R.string.upload_failed);
            }
        }

        @Override
        public void onCancel() {
            resetImageButtons();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case Consts.Code_Invoke_Image_Selector:
                    Uri uri = data.getData();
                    String path = FileUtil.getActualPath(this, uri);
                    if (!TextUtils.isEmpty(path)) {
                        uploadImage(path);
                    }
                    break;
                case Consts.Code_Invoke_Camera:
                    if (tmpUploadFile != null) {
                        uploadImage(tmpUploadFile.getAbsolutePath());
                    }
                    break;
                default:
                    break;
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reply_article, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }


}
