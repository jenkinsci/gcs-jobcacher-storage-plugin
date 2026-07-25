package jenkins.plugins.itemstorage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import jenkins.plugins.itemstorage.gcs.GCSItemStorage;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeCompatibilityTest {

    @Test
    @ConfiguredWithCode("gcs.yml")
    void shouldSupportConfigurationAsCodeForGcs(JenkinsConfiguredWithCodeRule jenkins) {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        GCSItemStorage gcsItemStorage = (GCSItemStorage) storage;
        assertThat(gcsItemStorage.getBucketName(), is("caches"));
        assertThat(gcsItemStorage.getPrefix(), is("the-prefix/"));
        assertThat(gcsItemStorage.getProjectId(), is("my-gcp-project"));
    }
}
