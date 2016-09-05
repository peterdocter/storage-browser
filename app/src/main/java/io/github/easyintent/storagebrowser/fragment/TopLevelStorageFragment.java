package io.github.easyintent.storagebrowser.fragment;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import io.github.easyintent.storagebrowser.R;
import io.github.easyintent.storagebrowser.data.DocumentFileTopLevelDir;
import io.github.easyintent.storagebrowser.data.TopLevelDir;
import io.github.easyintent.storagebrowser.data.repository.DefaultTopLevelRepository;
import io.github.easyintent.storagebrowser.data.repository.TopLevelDirRepository;

@EFragment
@OptionsMenu(R.menu.fragment_top_level_storage)
public class TopLevelStorageFragment extends ListFragment
        implements TopLevelDirActionListener {

    public static final String TAG = "top_level_storage_fragment";

    private static final Logger logger = LoggerFactory.getLogger(TopLevelStorageFragment.class);
    private static final int PICK_ROOT_DOCUMENT = 0x00d0;

    private TopLevelStorageListLoader topLevelStorageListLoader;

    public static TopLevelStorageFragment newInstance() {
        return new TopLevelStorageFragmentEx();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        reload();
    }

    @OptionsItem(R.id.refresh)
    public void reload() {
        if (isLoading()) {
            Toast.makeText(getActivity(), R.string.msg_please_wait, Toast.LENGTH_SHORT).show();
            return;
        }
        setListShown(false);
        applyTopLevelList(Action.LIST, null);
    }

    @OptionsItem(R.id.add_saf)
    protected void addStorageAccessDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, PICK_ROOT_DOCUMENT);
    }

    @Override
    public void onRemoveFromList(TopLevelDir topLevelDir) {
        applyTopLevelList(Action.REMOVE, topLevelDir);
    }


    @OnActivityResult(PICK_ROOT_DOCUMENT)
    protected void documentPicked(int resultCode, Intent data) {

        if (resultCode != Activity.RESULT_OK) {
            // do not care if cancelled
            return;
        }

        Context context = getActivity();
        Uri root = data.getData();

        ContentResolver resolver = context.getContentResolver();

        int flags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        resolver.takePersistableUriPermission(root, flags);

        DocumentFile document = DocumentFile.fromTreeUri(context, root);
        TopLevelDir topLevelDir = new DocumentFileTopLevelDir(document.getUri().toString());
        applyTopLevelList(Action.ADD, topLevelDir);
    }

    private synchronized void applyTopLevelList(Action action, TopLevelDir target) {
        topLevelStorageListLoader = new TopLevelStorageListLoader(getActivity(), action);
        topLevelStorageListLoader.execute(target);
    }

    private synchronized boolean isLoading() {
        return (topLevelStorageListLoader != null && topLevelStorageListLoader.isLoading());
    }

    private void onLoadChildrenListDone(List<TopLevelDir> topLevelDirs) {
        if (!isAdded()) {
            return;
        }
        showList(topLevelDirs);
    }

    private void onItemSelected(TopLevelDir topLevelDir) {
        FragmentManager manager = getFragmentManager();
        BrowseFragment browseFragment = BrowseFragment.newInstance();
        browseFragment.setCurrentDir(topLevelDir.createNode(getActivity()));
        manager.beginTransaction()
                .replace(R.id.content_view, browseFragment, BrowseFragment.TAG)
                .addToBackStack(null)
                .commit();
    }

    private void showList(List<TopLevelDir> topLevelDirs) {
        final TopLevelDirListAdapter adapter = new TopLevelDirListAdapter(getActivity(), topLevelDirs, this);
        setListAdapter(adapter);

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                TopLevelDir item = (TopLevelDir) adapterView.getItemAtPosition(i);
                if (item != null) {
                    onItemSelected(item);
                }
            }
        });

        if (topLevelDirs.isEmpty()) {
            setEmptyText(getString(R.string.msg_add_root));
        }

        setListShown(true);
    }


    private enum Action { LIST, ADD, REMOVE };

    private class TopLevelStorageListLoader extends AsyncTask<TopLevelDir,Object,List<TopLevelDir>> {

        private boolean loading;
        private Context context;
        private Action action;

        public TopLevelStorageListLoader(Context context, Action action) {
            this.context = context;
            this.action = action;
        }

        public boolean isLoading() {
            return loading;
        }

        @Override
        protected List<TopLevelDir> doInBackground(TopLevelDir... topLevelDirs) {
            loading = true;

            TopLevelDirRepository repo = new DefaultTopLevelRepository(context);
            switch (action) {
                case ADD:
                    repo.add(topLevelDirs[0]);
                    break;
                case REMOVE:
                    repo.remove(topLevelDirs[0]);
                    break;
                case LIST:
                    // will always list children
                    break;
            }

            List<TopLevelDir> list = repo.listAll();;

            loading = false;
            return list;
        }

        @Override
        protected void onPostExecute(List<TopLevelDir> topLevelDirs) {
            onLoadChildrenListDone(topLevelDirs);
        }
    }
}