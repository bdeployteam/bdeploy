package io.bdeploy.interfaces.manifest.statistics;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

public class ClientUsage {

	private final MetaManifest<ClientUsageData> meta;
	private final BHiveExecution hive;

	public ClientUsage(Manifest.Key instanceManifest, BHiveExecution hive) {
		this.hive = hive;
		this.meta = new MetaManifest<>(instanceManifest, false, ClientUsageData.class);
	}

	public void set(ClientUsageData data) {
		store(data); // overwrite
	}

	public ClientUsageData read() {
		return readOrCreate();
	}

	private ClientUsageData readOrCreate() {
		ClientUsageData stored = meta.read(hive);
		if (stored == null) {
			return new ClientUsageData();
		}
		return stored;
	}

	private void store(ClientUsageData data) {
		meta.write(hive, data);
	}

}
