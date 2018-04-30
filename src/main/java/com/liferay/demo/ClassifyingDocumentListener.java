package com.liferay.demo;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalServiceUtil;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;

import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;

@Component(immediate = true, service = ModelListener.class)
public class ClassifyingDocumentListener extends BaseModelListener<AssetEntry> {

	@Activate
	public void activate(BundleContext context) {
		String location = context.getBundle().getLocation();
		_log.info("bundle " + location + " activated!!!");
	}

	@Override
	public void onAfterCreate(AssetEntry model) throws ModelListenerException {
		_log.info("upload document! - onAfterCreate");
		classifyImage(model);
		super.onAfterCreate(model);
	}

	private void classifyImage(AssetEntry model) {

		try {
			String className = DLFileEntry.class.getName();
			if (model.getClassName().equals(className)) {
				long classPK = model.getClassPK();
				String[] tagsArray = clarifaiImage(model);
				if(tagsArray==null){
					_log.error("Your document won't be tagged, please check everything is alright (API keys etc)");
					return;
				}
				for (String tag : tagsArray) {
					_log.info(String.format(("tagging document tag: %s"), tag));
				}
				long[] categoryIds = null;
				AssetEntryLocalServiceUtil.updateEntry(model.getUserId(), model.getGroupId(), className, classPK,
						categoryIds, tagsArray);
			}
		} catch (Exception e) {
			_log.error(e);
		}

	}

	private String[] clarifaiImage(AssetEntry model) { // will only be called if
														// the AssetEntry is a
														// DLFileEntry
		List<ClarifaiOutput<Concept>> clarifaiResults = new ArrayList<ClarifaiOutput<Concept>>();
		List<String> tagList = new ArrayList<String>();
		try {
			DLFileEntry fileEntry = DLFileEntryLocalServiceUtil.getDLFileEntry(model.getClassPK());
			byte[] bytes = IOUtils.toByteArray(fileEntry.getContentStream());
			clarifaiResults = ClarifaiIntegrator.tagDocument(bytes);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(clarifaiResults==null)return null;
		for (ClarifaiOutput<Concept> result : clarifaiResults) {
			for (Concept data : result.data()) {
				System.out.println(String.format("%s=%s", data.name(), data.value()));
				if (data.value() > 0.5)
					tagList.add(data.name());
			}
		}
		return tagList.toArray(new String[0]);
	}

	private static final Log _log = LogFactoryUtil.getLog(ClassifyingDocumentListener.class);

}