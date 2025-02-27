describe('Create SGCluster', () => {
    Cypress.on('uncaught:exception', (err, runnable) => {
      return false
    });

    const namespace = Cypress.env('k8s_namespace')
    let resourceName;

    before( () => {
        cy.login()

        resourceName = Cypress._.random(0, 1e6)

        // Create SGObjectStorage dependency
        cy.createCRD('sgobjectstorages', {
            metadata: {
                namespace: namespace,
                name: 'storage-' + resourceName,
            },
            spec: {
                type: 's3Compatible',
                s3Compatible:{
                    forcePathStyle: true,
                    bucket: 'bucket',
                    awsCredentials: {
                        accessKeyId: 'api-key',
                        secretAccessKey: 'api-secret'
                    },
                    region: 'region',
                    endpoint: 'https://endpoint'
                }
            }
        });

        // Create SGScript
        cy.createCRD('sgscripts', {
            metadata: {
                name: 'script-' + resourceName, 
                namespace: namespace
            },
            spec: {
                continueOnError: false,
                managedVersions: true,
                scripts: [
                    {
                        storeStatusInDatabase: false, 
                        retryOnError: false, 
                        script: resourceName
                    }
                ]
            } 
        })

        // Create SGCluster dependency for spec.replicateFrom
        cy.createCRD('sgclusters', {
            metadata: {
                name: 'rep-sgcluster-' + resourceName, 
                namespace: namespace
            },
            spec: {
                instances: 1, 
                pods: {
                    persistentVolume: {
                        size: "128Mi"
                    }
                },
                nonProductionOptions: {
                    disableClusterPodAntiAffinity: true
                },
                postgres: {
                    version: "latest",
                    flavor: "vanilla"
                }
            }  
        });

    });

    beforeEach( () => {
        cy.gc()
        cy.login()
        cy.setCookie('sgReload', '0')
        cy.setCookie('sgTimezone', 'utc')
        cy.visit(namespace + '/sgclusters/new')
    });

    after( () => {
        cy.login()

        cy.deleteCluster(namespace, 'minimal-' + resourceName);
        
        cy.deleteCluster(namespace, 'basic-' + resourceName);

        cy.deleteCluster(namespace, 'babelfish-' + resourceName);

        cy.deleteCluster(namespace, 'advanced-' + resourceName);

        cy.deleteCluster(namespace, 'rep-sgcluster-' + resourceName);

        cy.deleteCRD('sgobjectstorages', {
            metadata: {
                name: 'storage-' + resourceName,
                namespace: namespace
            }
        });

        cy.deleteCRD('sgscripts', {
            metadata: {
                name: 'script-' + resourceName,
                namespace: namespace
            }
        });
    });

    it('Create SGCluster form should be visible', () => {
        cy.get('form#createCluster')
            .should('be.visible')
    });

    it('Creating a minimal SGCluster should be possible', () => {
        // Choose minimal wizard
        cy.get('[data-field="formTemplate.minimal"]')
            .click()

        // Test Cluster Name
        cy.get('[data-field="metadata.name"]')
            .type('minimal-' + resourceName)

        // Setup get and put mock to check resource is not found and all fields are correctly set
        cy.intercept('GET', '/stackgres/namespaces/' + namespace + '/sgclusters/minimal-' + resourceName)
            .as('getCluster')
        cy.intercept('POST', '/stackgres/sgclusters')
            .as('postCluster')

        // Test Submit form
        cy.get('form#createCluster button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Cluster "minimal-' + resourceName + '" created successfully')
            })

        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgclusters')

        // Test data sent to API
        cy.wait('@getCluster')
            .its('response.statusCode')
            .should('eq', 404)
        cy.wait('@postCluster')
            .its('response.statusCode')
            .should('eq', 200)
        cy.get('@postCluster')
            .its('request.body.spec.instances')
            .should('eq', 1)
        cy.get('@postCluster')
            .its('request.body.spec.profile')
            .should('eq', "development")
        cy.get('@postCluster')
            .its('request.body.spec.pods.persistentVolume.size')
            .should('eq', "1Gi")
        cy.get('@postCluster')
            .its('request.body.spec.postgres')
            .should('nested.include', {"flavor": "vanilla"})
            .and('nested.include', {"version": "latest"})
        cy.get('@postCluster')
            .its('request.body.spec.pods.disableConnectionPooling')
            .should('eq', true)
    });

    it('Creating a basic SGCluster should be possible', () => {
        // Choose basic wizard
        cy.get('[data-field="formTemplate.basic"]')
            .click()

        // Test Cluster Name
        cy.get('[data-field="metadata.name"]')
            .type('basic-' + resourceName)

        // Setup get and put mock to check resource is not found and all fields are correctly set
        cy.intercept('GET', '/stackgres/namespaces/' + namespace + '/sgclusters/basic-' + resourceName)
            .as('getCluster')
        cy.intercept('POST', '/stackgres/sgclusters')
            .as('postCluster')

        // Test Submit form
        cy.get('form#createCluster button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Cluster "basic-' + resourceName + '" created successfully')
            })

        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgclusters')

        // Test data sent to API
        cy.wait('@getCluster')
            .its('response.statusCode')
            .should('eq', 404)
        cy.wait('@postCluster')
            .its('response.statusCode')
            .should('eq', 200)
        cy.get('@postCluster')
            .its('request.body.spec.instances')
            .should('eq', 1)
        cy.get('@postCluster')
            .its('request.body.spec.profile')
            .should('eq', "testing")
        cy.get('@postCluster')
            .its('request.body.spec.pods.persistentVolume.size')
            .should('eq', "1Gi")
        cy.get('@postCluster')
            .its('request.body.spec.postgres')
            .should('nested.include', {"flavor": "vanilla"})
            .and('nested.include', {"version": "latest"})
        cy.get('@postCluster')
            .its('request.body.spec.prometheusAutobind')
            .should('eq', true)
        cy.get('@postCluster')
            .its('request.body.spec.distributedLogs')
            .should('have.nested.property','sgDistributedLogs')
    });

    it('Creating a full SGCluster should be possible', () => {
        // Choose basic wizard
        cy.get('[data-field="formTemplate.full"]')
            .click()

        // Test Cluster Name
        cy.get('[data-field="metadata.name"]')
            .type('full-' + resourceName)

        // Test Configurations
        cy.get('form#createCluster li[data-step="configurations"]')
            .click()
        
        // Test SGPostgresConfig file selection
        cy.get('select[data-field="spec.configurations.sgPostgresConfig"]')
            .select('createNewResource')

        cy.get('input#uploadSgPostgresConfig').selectFile('cypress/fixtures/forms/sgcluster/postgresql.conf')

        // Test SGPoolingConfig file selection
        cy.get('select[data-field="spec.configurations.sgPoolingConfig"]')
            .select('createNewResource')

        cy.get('input#uploadSgPoolingConfig').selectFile('cypress/fixtures/forms/sgcluster/pgbouncer.ini')

        // Setup get and put mock to check resource is not found and all fields are correctly set
        cy.intercept('GET', '/stackgres/namespaces/' + namespace + '/sgclusters/full-' + resourceName)
            .as('getCluster')
        cy.intercept('POST', '/stackgres/sgclusters')
            .as('postCluster')

        // Test Submit form
        cy.get('form#createCluster button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Cluster "full-' + resourceName + '" created successfully')
            })

        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgclusters')

        // Test data sent to API
        cy.wait('@getCluster')
            .its('response.statusCode')
            .should('eq', 404)
        cy.wait('@postCluster')
            .its('response.statusCode')
            .should('eq', 200)
        cy.get('@postCluster')
            .its('request.body.spec.instances')
            .should('eq', 1)
        cy.get('@postCluster')
            .its('request.body.spec.profile')
            .should('eq', "production")
        cy.get('@postCluster')
            .its('request.body.spec.pods.persistentVolume.size')
            .should('eq', "1Gi")
        cy.get('@postCluster')
            .its('request.body.spec.postgres')
            .should('nested.include', {"flavor": "vanilla"})
            .and('nested.include', {"version": "latest"})
        cy.get('@postCluster')
            .its('request.body.spec.prometheusAutobind')
            .should('eq', true)
        cy.get('@postCluster')
            .its('request.body.spec.distributedLogs')
            .should('have.nested.property','sgDistributedLogs')
        cy.get('@postCluster')
            .its('request.body.spec.configurations')
            .should('have.nested.property','sgPostgresConfig')
        cy.get('@postCluster')
            .its('request.body.spec.configurations')
            .should('have.nested.property','sgPoolingConfig')
    });

    it('Creating a SGCluster with Babelfish should be possible', () => {
        // Choose custom wizard
        cy.get('[data-field="formTemplate.custom"]')
            .click()

        // Test Cluster Name
        cy.get('input[data-field="metadata.name"]')
            .type('babelfish-' + resourceName)
        
        // Test enabling babelfish
        cy.get('label[data-field="spec.postgres.flavor.babelfish"]')
            .click()
        cy.get('input[data-field="spec.nonProductionOptions.enabledFeatureGates.babelfish"]')
            .click()

        // Setup get and put mock to check resource is not found and all fields are correctly set
        cy.intercept('GET', '/stackgres/namespaces/' + namespace + '/sgclusters/babelfish-' + resourceName)
            .as('getCluster')
        cy.intercept('POST', '/stackgres/sgclusters')
            .as('postCluster')

        // Test Submit form
        cy.get('form#createCluster button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Cluster "babelfish-' + resourceName + '" created successfully')
            })

        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgclusters')

        // Test data sent to API
        cy.wait('@getCluster')
            .its('response.statusCode')
            .should('eq', 404)
        cy.wait('@postCluster')
            .its('response.statusCode')
            .should('eq', 200)
        cy.get('@postCluster')
            .its('request.body.spec.instances')
            .should('eq', 1)
        cy.get('@postCluster')
            .its('request.body.spec.profile')
            .should('eq', "production")
        cy.get('@postCluster')
            .its('request.body.spec.pods.persistentVolume.size')
            .should('eq', "1Gi")
        cy.get('@postCluster')
            .its('request.body.spec.postgres')
            .should('nested.include', {"flavor": "babelfish"})
            .and('nested.include', {"version": "latest"})
        cy.get('@postCluster')
            .its('request.body.spec.nonProductionOptions')
            .should('nested.include',{'enabledFeatureGates[0]': "babelfish-flavor"})
    });

    it('Restore cluster from an SGBackup', () => {
        cy.visit(namespace + '/sgclusters/new?restoreFromBackup=ui-0&postgresVersion=' + Cypress.env('postgres_version'))

        // Advanced mode should be enabled
        cy.get('input#advancedMode')
            .should('be.checked')

        // Form should start at Initialization step
        cy.get('li[data-step="initialization"]')
            .should('have.class', 'active')

        // Backup selection graph should be visible
        cy.get('#pitr-graph .apexcharts-canvas')
            .should('be.visible')

        // Backup "ui-0" should be selected in the graph
        cy.get('.apexcharts-series-markers circle[selected="true"]')
            .should('be.visible')

        // Initialization should be "ui-0"
        cy.get('input[data-field="spec.initialData.restore.fromBackup"]')
            .should('be.visible')
            .should('have.value', 'ui-0')
            .should('be.disabled')

        // PITR input should be available
        cy.get('input[data-field="spec.initialData.restore.fromBackup.pointInTimeRecovery.restoreToTimestamp"]')
            .should('be.visible')
            .should('be.enabled')
            .should('have.value', '')
            .should('have.class', 'ready')
        
        // Cluster name should have a default value
        cy.get('li[data-step="cluster"]')
            .click()

        cy.get('input[data-field="metadata.name"]')
            .invoke('val').should('contain', 'restore-from-ui-0-')

        // Postgres version should match that of backup's
        cy.get('ul#postgresVersion li.selected')
            .invoke('text').should('contain', 'Postgres ' + Cypress.env('postgres_version'))

    });

    it('Creating an advanced SGCluster should be possible', () => {
        // Choose custom wizard
        cy.get('[data-field="formTemplate.custom"]')
            .click()

        // Enable advanced options
        cy.get('form#createCluster input#advancedMode')
            .click()
        
        // Test Cluster Name
        cy.get('input[data-field="metadata.name"]')
            .type('advanced-' + resourceName)
        
        // Test Profile
        cy.get('select[data-field="spec.profile"]')
            .select('testing')

        // Test postgres version
        cy.get('ul[data-field="spec.postgres.version"] li').first()
            .click()
        cy.get('ul[data-field="spec.postgres.version"] a[data-val="' + Cypress.env('postgres_version') + '"]')
            .click()

        // Enable SSL Connections
        cy.get('input[data-field="spec.postgres.ssl.enabled"]')
            .click()
        cy.get('input[data-field="spec.postgres.ssl.certificateSecretKeySelector.name"]')
            .type('cert-cluster')
        cy.get('input[data-field="spec.postgres.ssl.certificateSecretKeySelector.key"]')
            .type('tls.crt')
        cy.get('input[data-field="spec.postgres.ssl.privateKeySecretKeySelector.name"]')
            .type('cert-cluster')
        cy.get('input[data-field="spec.postgres.ssl.privateKeySecretKeySelector.key"]')
            .type('tls.key')
        
        // Test instances
        cy.get('input[data-field="spec.instances"]')
            .clear()
            .type('4')    
        
        // Test Volume Size
        cy.get('input[data-field="spec.pods.persistentVolume.size"]')
            .clear()
            .type('2')

        // Test some extensions
        cy.get('form#createCluster li[data-step="extensions"]')
            .click()

        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.db_info"].enableExtension')
            .click()
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.pg_repack"].enableExtension')
            .click()
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.plpgsql_check"].enableExtension')
            .click()
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.http"].enableExtension')
            .click()
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.hostname"].enableExtension')
            .click()

        // Test managed backups configuration
        cy.get('form#createCluster li[data-step="backups"]')
            .click()

        cy.get('label[data-field="spec.configurations.backups"]')
            .click()

        // Storage Details
        cy.get('[data-field="spec.configurations.backups.sgObjectStorage"]')
            .select('storage-' + resourceName)
        
        // Backup Schedule
        cy.get('#backupConfigFullScheduleMin')
            .clear()
            .type('1')
        
        cy.get('#backupConfigFullScheduleHour')
            .clear()    
            .type('1')

        cy.get('#backupConfigFullScheduleDOM')
            .clear()    
            .type('1')
        
        cy.get('#backupConfigFullScheduleMonth')
            .clear()    
            .type('1')

        cy.get('#backupConfigFullScheduleDOW')
            .clear()    
            .type('1')

        cy.get('[data-field="spec.configurations.backups.retention"]')
            .clear()    
            .type('3')

        // Base Backup Details
        cy.get('[data-field="spec.configurations.backups.path"]')
            .clear()    
            .type('/path')
        
        cy.get('[data-field="spec.configurations.backups.compression"]')
            .select('LZMA')

        //Performance Details
        cy.get('[data-field="spec.configurations.backups.performance.maxNetworkBandwidth"]')
            .type('1024')

        cy.get('[data-field="spec.configurations.backups.performance.maxDiskBandwidth"]')
            .type('1024')
        
        cy.get('[data-field="spec.configurations.backups.performance.uploadDiskConcurrency"]')
            .clear()    
            .type('2')
        
        // Volume Snapshot details
        cy.get('label[data-field="spec.configurations.backups.useVolumeSnapshot"]')
            .click()

        cy.get('[data-field="spec.configurations.backups.volumeSnapshotClass"]')
            .clear()    
            .type('snapshot')

        cy.get('label[data-field="spec.configurations.backups.fastVolumeSnapshot"]')
            .click()

        // Test data initialization
        cy.get('form#createCluster li[data-step="initialization"]')
            .click()
        
        // Choose Backup (We're always assuming there's a backup with name "ui-0" on the specified namespace)
        cy.get('#pitr-graph .apexcharts-series-markers > circle[rel="0"]')
            .click()
         
        // Set PITR
        cy.get('input[data-field="spec.initialData.restore.fromBackup.pointInTimeRecovery.restoreToTimestamp"]')
            .clear()
            .type('9999-01-01 00:00:00')
        
        // Performance details
        cy.get('input[data-field="spec.initialData.restore.downloadDiskConcurrency"]') 
            .clear()
            .type('2')

        // Test replicate from external instance
        cy.get('form#createCluster li[data-step="replicate-from"]')
        .click()

        cy.get('select[data-field="spec.replicateFrom.source"]') 
            .select('external-storage')

        cy.get('input[data-field="spec.replicateFrom.instance.external.host"]') 
            .type('host')

        cy.get('input[data-field="spec.replicateFrom.instance.external.port"]') 
            .type('1111')

        cy.get('select[data-field="spec.replicateFrom.storage.sgObjectStorage"]')
            .select('storage-' + resourceName)

        cy.get('input[data-field="spec.replicateFrom.storage.path"]')
            .type('/path')

        cy.get('input[data-field="spec.replicateFrom.storage.performance.downloadConcurrency"]')
            .type(1)
        
        cy.get('input[data-field="spec.replicateFrom.storage.performance.maxDiskBandwidth"]')
            .type(2)

        cy.get('input[data-field="spec.replicateFrom.storage.performance.maxNetworkBandwidth"]')
            .type(3)

        cy.get('input[data-field="spec.replicateFrom.users.superuser.username.name"]') 
            .type('name')
        
        cy.get('input[data-field="spec.replicateFrom.users.superuser.username.key"]') 
            .type('key')
        
        cy.get('input[data-field="spec.replicateFrom.users.superuser.password.name"]') 
            .type('name')
        
        cy.get('input[data-field="spec.replicateFrom.users.superuser.password.key"]') 
            .type('key')

        cy.get('input[data-field="spec.replicateFrom.users.replication.username.name"]') 
            .type('name')
        
        cy.get('input[data-field="spec.replicateFrom.users.replication.username.key"]') 
            .type('key')
        
        cy.get('input[data-field="spec.replicateFrom.users.replication.password.name"]') 
            .type('name')
        
        cy.get('input[data-field="spec.replicateFrom.users.replication.password.key"]') 
            .type('key')

        cy.get('input[data-field="spec.replicateFrom.users.authenticator.username.name"]') 
            .type('name')
        
        cy.get('input[data-field="spec.replicateFrom.users.authenticator.username.key"]') 
            .type('key')
        
        cy.get('input[data-field="spec.replicateFrom.users.authenticator.password.name"]') 
            .type('name')
        
        cy.get('input[data-field="spec.replicateFrom.users.authenticator.password.key"]') 
            .type('key')

        // Test scripts
        cy.get('form#createCluster li[data-step="scripts"]')
            .click()
        
        // Test create new script
        cy.get('.scriptFieldset > div.fieldsetFooter > a.addRow')
            .click()

        // Test Entry script textarea
        cy.get('[data-field="spec.managedSql.scripts[0].scriptSpec.scripts[0].script"]')
            .type(resourceName)        
        
        // Test Add Script button
        cy.get('.scriptFieldset > div.fieldsetFooter > a.addRow')
            .click()

        // Test select script
        cy.get('[data-field="spec.managedSql.scripts.scriptSource[1]"]')
            .select('script-' + resourceName)

        // Test prometheus autobind
        cy.get('form#createCluster li[data-step="sidecars"]')
            .click()

        cy.get('input[data-field="spec.prometheusAutobind"]')
            .click()

        // Test User-Supplied Pods Sidecars
        // Test Custom volumes
        cy.get('div.repeater.customVolumes .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[0].name"]')
            .type('vol1')

        cy.get('select[data-field="spec.pods.customVolumes[0].type"]')
            .select('emptyDir')

        cy.get('input[data-field="spec.pods.customVolumes[0].emptyDir.medium"]')
            .type('medium')
        
        cy.get('input[data-field="spec.pods.customVolumes[0].emptyDir.sizeLimit"]')
            .type('sizeLimit')
        
        cy.get('fieldset[data-fieldset="spec.pods.customVolumes"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[1].name"]')
            .type('vol2')

        cy.get('select[data-field="spec.pods.customVolumes[1].type"]')
            .select('configMap')

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.name"]')
            .type('name')

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.optional"]')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.defaultMode"]')
            .type('0')
        
        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[0].key"]')
            .type('key1')
        
        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[0].mode"]')
            .type('0')

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[0].path"]')
            .type('path')
        
        // Note: Disabled until repeater gets optimized. Causes test to wait and fail
        /* cy.get('fieldset[data-field="spec.pods.customVolumes[1].configMap.items"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[1].key"]')
            .type('key2')
        
        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[1].mode"]')
            .type('0')

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[1].path"]')
            .type('path2')
        
        cy.get('fieldset[data-field="spec.pods.customVolumes[1].configMap.items"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('div[data-field="spec.pods.customVolumes[1].configMap.items[2]"] a.delete')
            .click() */

        cy.get('fieldset[data-fieldset="spec.pods.customVolumes"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[2].name"]')
            .type('vol3')

        cy.get('select[data-field="spec.pods.customVolumes[2].type"]')
            .select('secret')

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.secretName"]')
            .type('name')

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.optional"]')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.defaultMode"]')
            .type('0')
        
        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[0].key"]')
            .type('key1')
        
        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[0].mode"]')
            .type('0')

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[0].path"]')
            .type('path')
        
        // Note: Disabled until repeater gets optimized. Causes test to wait and fail
        /* cy.get('fieldset[data-field="spec.pods.customVolumes[2].secret.items"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[1].key"]')
            .type('key2')
        
        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[1].mode"]')
            .type('0')

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[1].path"]')
            .type('path2')
        
        cy.get('fieldset[data-field="spec.pods.customVolumes[2].secret.items"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('div[data-field="spec.pods.customVolumes[2].secret.items[2]"] a.delete')
            .click()
        */

        // Test Custom Init Containers
        cy.get('div.repeater.customInitContainers .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].name"]')
            .type('container1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].image"]')
            .type('image1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].imagePullPolicy"]')
            .type('imagePullPolicy1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].workingDir"]')
            .type('workingDir1')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].args[0]"]')
            .type('arg1')

        cy.get('fieldset[data-field="spec.pods.customInitContainers[0].args"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].args[1]"]')
            .type('arg2')
        
        cy.get('fieldset[data-field="spec.pods.customInitContainers[0].args"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].args[2]"] + a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].command[0]"]')
            .type('command1')

        cy.get('fieldset[data-field="spec.pods.customInitContainers[0].command"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].command[1]"]')
            .type('command2')

        cy.get('fieldset[data-field="spec.pods.customInitContainers[0].command"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].command[2]"] + a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].env[0].name"]')
            .type('var1')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].env[0].value"]')
            .type('val1')

        cy.get('fieldset[data-field="spec.pods.customInitContainers[0].env"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].env[1].name"]')
            .type('var2')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].env[1].value"]')
            .type('val2')

        cy.get('fieldset[data-field="spec.pods.customInitContainers[0].env"] + .fieldsetFooter .addRow')
            .click()

        cy.get('div[data-field="spec.pods.customInitContainers[0].env[2]"] a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].name"]')
            .type('port1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].hostIP"]')
            .type('ip1')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].hostPort"]')
            .type('1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].containerPort"]')
            .type('1')
        
        cy.get('select[data-field="spec.pods.customInitContainers[0].ports[0].protocol"]')
            .select('TCP')

        cy.get('fieldset[data-field="spec.pods.customInitContainers.ports"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].name"]')
            .type('port2')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].hostIP"]')
            .type('ip2')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].hostPort"]')
            .type('2')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].containerPort"]')
            .type('2')
        
        cy.get('select[data-field="spec.pods.customInitContainers[0].ports[1].protocol"]')
            .select('UDP')

        cy.get('fieldset[data-field="spec.pods.customInitContainers.ports"] + .fieldsetFooter .addRow')
            .click()

        cy.get('div[data-field="spec.pods.customInitContainers[0].ports[2]"] a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].name"]')
            .type('vol1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].readOnly"]')
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].mountPath"]')
            .type('mountPath')

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].mountPropagation"]')
            .type('mountPropagation')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].subPath"]')
            .type('subPath')

        cy.get('fieldset[data-field="spec.pods.customInitContainers[0].volumeMounts"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('div[data-field="spec.pods.customInitContainers[0].volumeMounts[1]"] a.delete')
            .click()

        cy.get('fieldset[data-fieldset="spec.pods.customInitContainers"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('div[data-field="spec.pods.customInitContainers[1]"] > .header a.delete')
            .click()

        // Test Custom Containers
        cy.get('div.repeater.customContainers .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].name"]')
            .type('container1')

        cy.get('input[data-field="spec.pods.customContainers[0].image"]')
            .type('image1')

        cy.get('input[data-field="spec.pods.customContainers[0].imagePullPolicy"]')
            .type('imagePullPolicy1')

        cy.get('input[data-field="spec.pods.customContainers[0].workingDir"]')
            .type('workingDir1')
        
        cy.get('input[data-field="spec.pods.customContainers[0].args[0]"]')
            .type('arg1')

        cy.get('fieldset[data-field="spec.pods.customContainers[0].args"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].args[1]"]')
            .type('arg2')
        
        cy.get('fieldset[data-field="spec.pods.customContainers[0].args"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].args[2]"] + a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].command[0]"]')
            .type('command1')

        cy.get('fieldset[data-field="spec.pods.customContainers[0].command"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].command[1]"]')
            .type('command2')

        cy.get('fieldset[data-field="spec.pods.customContainers[0].command"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].command[2]"] + a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].env[0].name"]')
            .type('var1')
        
        cy.get('input[data-field="spec.pods.customContainers[0].env[0].value"]')
            .type('val1')

        cy.get('fieldset[data-field="spec.pods.customContainers[0].env"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].env[1].name"]')
            .type('var2')
        
        cy.get('input[data-field="spec.pods.customContainers[0].env[1].value"]')
            .type('val2')

        cy.get('fieldset[data-field="spec.pods.customContainers[0].env"] + .fieldsetFooter .addRow')
            .click()

        cy.get('div[data-field="spec.pods.customContainers[0].env[2]"] a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].name"]')
            .type('port1')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].hostIP"]')
            .type('ip1')
        
        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].hostPort"]')
            .type('1')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].containerPort"]')
            .type('1')
        
        cy.get('select[data-field="spec.pods.customContainers[0].ports[0].protocol"]')
            .select('TCP')

        cy.get('fieldset[data-field="spec.pods.customContainers.ports"] + .fieldsetFooter .addRow')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].name"]')
            .type('port2')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].hostIP"]')
            .type('ip2')
        
        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].hostPort"]')
            .type('2')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].containerPort"]')
            .type('2')
        
        cy.get('select[data-field="spec.pods.customContainers[0].ports[1].protocol"]')
            .select('UDP')

        cy.get('fieldset[data-field="spec.pods.customContainers.ports"] + .fieldsetFooter .addRow')
            .click()

        cy.get('div[data-field="spec.pods.customContainers[0].ports[2]"] a.delete')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].name"]')
            .type('vol1')

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].readOnly"]')
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].mountPath"]')
            .type('mountPath')

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].mountPropagation"]')
            .type('mountPropagation')
        
        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].subPath"]')
            .type('subPath')

        cy.get('fieldset[data-field="spec.pods.customContainers[0].volumeMounts"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('div[data-field="spec.pods.customContainers[0].volumeMounts[1]"] a.delete')
            .click()

        cy.get('fieldset[data-fieldset="spec.pods.customContainers"] + .fieldsetFooter a.addRow')
            .click()

        cy.get('div[data-field="spec.pods.customContainers[1]"] > .header a.delete')
            .click()

        // Test Replication
        cy.get('form#createCluster li[data-step="pods-replication"]')
            .click()
        
        cy.get('select[data-field="spec.replication.role"]')
            .select('ha')
        
        cy.get('select[data-field="spec.replication.mode"]')
            .select('sync')

        cy.get('input[data-field="spec.replication.syncInstances"]')
            .clear()
            .type('2')

        cy.get('[data-add="spec.replication.groups"]')
            .click()

        cy.get('[data-group="replication-group-0"] input[data-field="spec.replication.groups[0].name"]')
            .clear()
            .type('group-0')
        
        cy.get('[data-group="replication-group-0"] select[data-field="spec.replication.groups[0].role"]')
            .select('readonly')
        
        cy.get('[data-group="replication-group-0"] input[data-field="spec.replication.groups[0].instances"]')
            .clear()
            .type('1')
        
        cy.get('[data-add="spec.replication.groups"]')
            .click()

        cy.get('[data-group="replication-group-1"] input[data-field="spec.replication.groups[1].name"]')
            .clear()
            .type('group-1')
        
        cy.get('[data-group="replication-group-1"] select[data-field="spec.replication.groups[1].role"]')
            .select('none')
        
        cy.get('[data-group="replication-group-1"] input[data-field="spec.replication.groups[1].instances"]')
            .clear()
            .type('1')

        // Test Postgres Services
        cy.get('form#createCluster li[data-step="services"]')
            .click()

        cy.get('select[data-field="spec.postgresServices.primary.type"]')
            .select('LoadBalancer')
        
        cy.get('input[data-field="spec.postgresServices.primary.loadBalancerIP"]')
            .clear()
            .type('1.2.3.4')

        cy.get('div.repeater.sidecars.primary div.fieldsetFooter > a.addRow')
            .click()

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[0].appProtocol"]')
            .clear()
            .type('protocol')
        
        cy.get('input[data-field="spec.postgresServices.primary.customPorts[0].name"]')
            .clear()
            .type('name')
        
        cy.get('input[data-field="spec.postgresServices.primary.customPorts[0].nodePort"]')
            .clear()
            .type('1234')

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[0].port"]')
            .clear()
            .type('1234')

        cy.get('select[data-field="spec.postgresServices.primary.customPorts[0].protocol"]')
            .select('UDP')

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[0].targetPort"]')
            .clear()
            .type('1234')

        cy.get('fieldset[data-field="spec.postgresServices.primary.customPorts"] + div.fieldsetFooter > a.addRow')
            .click()

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].appProtocol"]')
            .clear()
            .type('protocol2')
        
        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].name"]')
            .clear()
            .type('name2')
        
        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].nodePort"]')
            .clear()
            .type('4321')

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].port"]')
            .clear()
            .type('4321')

        cy.get('select[data-field="spec.postgresServices.primary.customPorts[1].protocol"]')
            .select('SCTP')
        
        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].targetPort"]')
            .clear()
            .type('4321')

        cy.get('select[data-field="spec.postgresServices.replicas.type"]')
            .select('NodePort')
        
        cy.get('input[data-field="spec.postgresServices.replicas.loadBalancerIP"]')
            .clear()
            .type('1.2.3.4')

        cy.get('div.repeater.sidecars.replica div.fieldsetFooter > a.addRow')
            .click()

        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].appProtocol"]')
            .clear()
            .type('protocol')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].name"]')
            .clear()
            .type('name')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].nodePort"]')
            .clear()
            .type('1234')

        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].port"]')
            .clear()
            .type('1234')
    
        cy.get('select[data-field="spec.postgresServices.replicas.customPorts[0].protocol"]')
            .select('UDP')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].targetPort"]')
            .clear()
            .type('1234')

        cy.get('fieldset[data-field="spec.postgresServices.replicas.customPorts"] + div.fieldsetFooter > a.addRow')
            .click()

        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[1].appProtocol"]')
            .clear()
            .type('protocol2')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[1].name"]')
            .clear()
            .type('name2')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[1].nodePort"]')
            .clear()
            .type('4321')

        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[1].port"]')
            .clear()
            .type('4321')

        cy.get('select[data-field="spec.postgresServices.replicas.customPorts[1].protocol"]')
            .select('SCTP')

        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[1].targetPort"]')
            .clear()
            .type('4321')

        // Test Metadata
        cy.get('form#createCluster li[data-step="metadata"]')
            .click()

        cy.get('fieldset[data-field="spec.metadata.labels.clusterPods"] + div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.metadata.labels.clusterPods[0].label"]')
            .type('label')
        cy.get('input[data-field="spec.metadata.labels.clusterPods[0].value"]')
            .type('value')
        
        cy.get('fieldset[data-field="spec.metadata.annotations.allResources"] + div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.metadata.annotations.allResources[0].annotation"]')
            .type('annotation')
        cy.get('input[data-field="spec.metadata.annotations.allResources[0].value"]')
            .type('value')

        cy.get('fieldset[data-field="spec.metadata.annotations.clusterPods"] + div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.metadata.annotations.clusterPods[0].annotation"]')
            .type('annotation')
        cy.get('input[data-field="spec.metadata.annotations.clusterPods[0].value"]')
            .type('value')

        cy.get('fieldset[data-field="spec.metadata.annotations.services"] + div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.metadata.annotations.services[0].annotation"]')
            .type('annotation')        
        cy.get('input[data-field="spec.metadata.annotations.services[0].value"]')
            .type('value')
        
        cy.get('fieldset[data-field="spec.metadata.annotations.primaryService"] + div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.metadata.annotations.primaryService[0].annotation"]')
            .type('annotation')        
        cy.get('input[data-field="spec.metadata.annotations.primaryService[0].value"]')
            .type('value')
        
        cy.get('fieldset[data-field="spec.metadata.annotations.replicasService"] + div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.metadata.annotations.replicasService[0].annotation"]')
            .type('annotation')        
        cy.get('input[data-field="spec.metadata.annotations.replicasService[0].value"]')
            .type('value')

        // Tests Scheduling
        cy.get('form#createCluster li[data-step="scheduling"]')
            .click()

        // Tests Node Selectors
        cy.get('div.repeater.nodeSelector div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.pods.scheduling.nodeSelector[0].label"]')
            .type('key')
        cy.get('input[data-field="spec.pods.scheduling.nodeSelector[0].value"]')
            .type('value')

        // Tests Node Tolerations
        cy.get('div.scheduling.repeater.tolerations div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.pods.scheduling.tolerations[0].key"]')
            .type('key')
        cy.get('input[data-field="spec.pods.scheduling.tolerations[0].value"]')
            .type('value')
        cy.get('select[data-field="spec.pods.scheduling.tolerations[0].effect"]')
            .select('NoSchedule')
        
        // Tests Node Affinity (Required)
        cy.get('div.scheduling.repeater.requiredAffinity div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchExpressions.items.properties.key"]')
            .type('key')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchExpressions.items.properties.operator"]')
            .select('In')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchExpressions.items.properties.values"]')
            .type('value')

        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchFields.items.properties.key"]')
            .type('key')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchFields.items.properties.operator"]')
            .select('In')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchFields.items.properties.values"]')
            .type('value')
        
        // Tests Node Affinity (Preferred)
        cy.get('div.scheduling.repeater.preferredAffinity div.fieldsetFooter > a.addRow')
            .click()
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchExpressions.items.properties.key"]')
            .type('key')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchExpressions.items.properties.operator"]')
            .select('In')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchExpressions.items.properties.values"]')
            .type('value')
        
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchFields.items.properties.key"]')
            .type('key')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchFields.items.properties.operator"]')
            .select('In')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchFields.items.properties.values"]')
            .type('value')

        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.weight"]')
            .clear()
            .type('10')

        // Test Non Production Options
        cy.get('form#createCluster li[data-step="non-production"]')
            .click()

        cy.get('select[data-field="spec.nonProductionOptions.disableClusterPodAntiAffinity"]')
            .select('Disable')
        
        cy.get('select[data-field="spec.nonProductionOptions.disablePatroniResourceRequirements"]')
            .select('Disable')
        
        cy.get('select[data-field="spec.nonProductionOptions.disableClusterResourceRequirements"]')
            .select('Disable')

        // Test Dry Run
        cy.get('form#createCluster button[data-field="dryRun"]')
            .click()

        cy.get('#clusterSummary')
            .should('be.visible')

        cy.get('#clusterSummary span.close')
            .click()

        cy.get('#clusterSummary')
            .should('not.exist')

        // Setup get and put mock to check resource is not found and all fields are correctly set
        cy.intercept('GET', '/stackgres/namespaces/' + namespace + '/sgclusters/advanced-' + resourceName)
            .as('getCluster')
        cy.intercept('POST', '/stackgres/sgclusters').as('postCluster')

        // Test Submit form
        cy.get('form#createCluster button[type="submit"]')
            .click()

        // Test creation notification
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Cluster "advanced-' + resourceName + '" created successfully')
            })

        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgclusters')

        // Test data sent to API
        cy.wait('@getCluster')
            .its('response.statusCode')
            .should('eq', 404)
        cy.wait('@postCluster')
            .its('response.statusCode')
            .should('eq', 200)
        cy.get('@postCluster')
            .its('request.body.spec.instances')
            .should('eq', "4")
        cy.get('@postCluster')
            .its('request.body.spec.pods.persistentVolume.size')
            .should('eq', "2Gi")
        cy.get('@postCluster')
            .its('request.body.spec.postgres.ssl')
            .should('nested.include', {"enabled": true})
            .and('nested.include', {"certificateSecretKeySelector.name": "cert-cluster"})
            .and('nested.include', {"certificateSecretKeySelector.key": "tls.crt"})
            .and('nested.include', {"privateKeySecretKeySelector.name": "cert-cluster"})
            .and('nested.include', {"privateKeySecretKeySelector.key": "tls.key"})
        cy.get('@postCluster')
            .its('request.body.spec.postgres.extensions')
            .should('have.lengthOf', 5)
            .then((list) => Cypress._.map(list, 'name'))
            .should('include', "db_info")
            .and('include', "pg_repack")
            .and('include', "plpgsql_check")
            .and('include', "http")
            .and('include', "hostname")
        cy.get('@postCluster')
            .its('request.body.spec.configurations.backups')
            .its(0)
            .should('nested.include', {"sgObjectStorage": 'storage-' + resourceName})
            .and('nested.include', {"cronSchedule": "1 1 1 1 1"})
            .and('nested.include', {"retention": "3"})
            .and('nested.include', {"path": "/path"})
            .and('nested.include', {"compression": "lzma"})
            .and('nested.include', {"performance.maxNetworkBandwidth": "1024"})
            .and('nested.include', {"performance.maxDiskBandwidth": "1024"})
            .and('nested.include', {"performance.uploadDiskConcurrency": "2"})
            .and('nested.include', {"useVolumeSnapshot": true})
            .and('nested.include', {"volumeSnapshotClass": "snapshot"})
            .and('nested.include', {"fastVolumeSnapshot": true})
        cy.get('@postCluster')
            .its('request.body.spec.initialData.restore')
            .should('nested.include', {"fromBackup.name": "ui-0"})
            .and('nested.include', {"downloadDiskConcurrency": "2"})
            .and('have.nested.property', "fromBackup.pointInTimeRecovery.restoreToTimestamp")
        cy.get('@postCluster')
            .its('request.body.spec.managedSql')
            .should('nested.include', {"scripts[0].scriptSpec.scripts[0].script": '' + resourceName})
            .and('nested.include', {"scripts[1].sgScript": 'script-' + resourceName})
        cy.get('@postCluster')
            .its('request.body.spec.prometheusAutobind')
            .should('eq', true)
        cy.get('@postCluster')
            .its('request.body.spec.pods')
            .should('nested.include', {"customVolumes[0].name": 'vol1'})
            .and('nested.include', {"customVolumes[0].emptyDir.medium": 'medium'})
            .and('nested.include', {"customVolumes[0].emptyDir.sizeLimit": 'sizeLimit'})
            .and('nested.include', {"customVolumes[1].name": 'vol2'})
            .and('nested.include', {"customVolumes[1].configMap.name": 'name'})
            .and('nested.include', {"customVolumes[1].configMap.optional": false})
            .and('nested.include', {"customVolumes[1].configMap.defaultMode": '0'})
            .and('nested.include', {"customVolumes[1].configMap.items[0].key": 'key1'})
            .and('nested.include', {"customVolumes[1].configMap.items[0].mode": '0'})
            .and('nested.include', {"customVolumes[1].configMap.items[0].path": 'path'})
            // Note: Disabled until repeater gets optimized. Causes test to wait and fail
            /* .and('nested.include', {"customVolumes[1].configMap.items[1].key": 'key2'})
            .and('nested.include', {"customVolumes[1].configMap.items[1].mode": '0'})
            .and('nested.include', {"customVolumes[1].configMap.items[1].path": 'path2'}) */
            .and('nested.include', {"customVolumes[2].name": 'vol3'})
            .and('nested.include', {"customVolumes[2].secret.secretName": 'name'})
            .and('nested.include', {"customVolumes[2].secret.optional": false})
            .and('nested.include', {"customVolumes[2].secret.defaultMode": '0'})
            .and('nested.include', {"customVolumes[2].secret.items[0].key": 'key1'})
            .and('nested.include', {"customVolumes[2].secret.items[0].mode": '0'})
            .and('nested.include', {"customVolumes[2].secret.items[0].path": 'path'})
            // Note: Disabled until repeater gets optimized. Causes test to wait and fail
            /* .and('nested.include', {"customVolumes[2].secret.items[1].key": 'key2'})
            .and('nested.include', {"customVolumes[2].secret.items[1].mode": '0'})
            .and('nested.include', {"customVolumes[2].secret.items[1].path": 'path2'}) */
            .and('nested.include', {"customInitContainers[0].name": 'container1'})
            .and('nested.include', {"customInitContainers[0].image": 'image1'})
            .and('nested.include', {"customInitContainers[0].imagePullPolicy": 'imagePullPolicy1'})
            .and('nested.include', {"customInitContainers[0].workingDir": 'workingDir1'})
            .and('nested.include', {"customInitContainers[0].args[0]": 'arg1'})
            .and('nested.include', {"customInitContainers[0].args[1]": 'arg2'})
            .and('nested.include', {"customInitContainers[0].command[0]": 'command1'})
            .and('nested.include', {"customInitContainers[0].command[1]": 'command2'})
            .and('nested.include', {"customInitContainers[0].env[0].name": 'var1'})
            .and('nested.include', {"customInitContainers[0].env[0].value": 'val1'})
            .and('nested.include', {"customInitContainers[0].env[1].name": 'var2'})
            .and('nested.include', {"customInitContainers[0].env[1].value": 'val2'})
            .and('nested.include', {"customInitContainers[0].ports[0].name": 'port1'})
            .and('nested.include', {"customInitContainers[0].ports[0].hostIP": 'ip1'})
            .and('nested.include', {"customInitContainers[0].ports[0].hostPort": '1'})
            .and('nested.include', {"customInitContainers[0].ports[0].containerPort": '1'})
            .and('nested.include', {"customInitContainers[0].ports[0].protocol": 'TCP'})
            .and('nested.include', {"customInitContainers[0].ports[1].name": 'port2'})
            .and('nested.include', {"customInitContainers[0].ports[1].hostIP": 'ip2'})
            .and('nested.include', {"customInitContainers[0].ports[1].hostPort": '2'})
            .and('nested.include', {"customInitContainers[0].ports[1].containerPort": '2'})
            .and('nested.include', {"customInitContainers[0].ports[1].protocol": 'UDP'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].name": 'vol1'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].readOnly": true})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].mountPath": 'mountPath'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].mountPropagation": 'mountPropagation'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].subPath": 'subPath'})
            .and('nested.include', {"customContainers[0].name": 'container1'})
            .and('nested.include', {"customContainers[0].image": 'image1'})
            .and('nested.include', {"customContainers[0].imagePullPolicy": 'imagePullPolicy1'})
            .and('nested.include', {"customContainers[0].workingDir": 'workingDir1'})
            .and('nested.include', {"customContainers[0].args[0]": 'arg1'})
            .and('nested.include', {"customContainers[0].args[1]": 'arg2'})
            .and('nested.include', {"customContainers[0].command[0]": 'command1'})
            .and('nested.include', {"customContainers[0].command[1]": 'command2'})
            .and('nested.include', {"customContainers[0].env[0].name": 'var1'})
            .and('nested.include', {"customContainers[0].env[0].value": 'val1'})
            .and('nested.include', {"customContainers[0].env[1].name": 'var2'})
            .and('nested.include', {"customContainers[0].env[1].value": 'val2'})
            .and('nested.include', {"customContainers[0].ports[0].name": 'port1'})
            .and('nested.include', {"customContainers[0].ports[0].hostIP": 'ip1'})
            .and('nested.include', {"customContainers[0].ports[0].hostPort": '1'})
            .and('nested.include', {"customContainers[0].ports[0].containerPort": '1'})
            .and('nested.include', {"customContainers[0].ports[0].protocol": 'TCP'})
            .and('nested.include', {"customContainers[0].ports[1].name": 'port2'})
            .and('nested.include', {"customContainers[0].ports[1].hostIP": 'ip2'})
            .and('nested.include', {"customContainers[0].ports[1].hostPort": '2'})
            .and('nested.include', {"customContainers[0].ports[1].containerPort": '2'})
            .and('nested.include', {"customContainers[0].ports[1].protocol": 'UDP'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].name": 'vol1'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].readOnly": true})
            .and('nested.include', {"customContainers[0].volumeMounts[0].mountPath": 'mountPath'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].mountPropagation": 'mountPropagation'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].subPath": 'subPath'})
        cy.get('@postCluster')
            .its('request.body.spec.replicateFrom')
            .should('nested.include', {"instance.external.host": 'host'})
            .and('nested.include', {"instance.external.port": '1111'})
            .and('nested.include', {"storage.sgObjectStorage": 'storage-' + resourceName})
            .and('nested.include', {"storage.path": '/path'})
            .and('nested.include', {"storage.performance.downloadConcurrency": '1'})
            .and('nested.include', {"storage.performance.maxDiskBandwidth": '2'})
            .and('nested.include', {"storage.performance.maxNetworkBandwidth": '3'})
            .and('nested.include', {"users.superuser.username.name": 'name'})
            .and('nested.include', {"users.superuser.username.key": 'key'})
            .and('nested.include', {"users.superuser.password.name": 'name'})
            .and('nested.include', {"users.superuser.password.key": 'key'})
            .and('nested.include', {"users.replication.username.name": 'name'})
            .and('nested.include', {"users.replication.username.key": 'key'})
            .and('nested.include', {"users.replication.password.name": 'name'})
            .and('nested.include', {"users.replication.password.key": 'key'})
            .and('nested.include', {"users.authenticator.username.name": 'name'})
            .and('nested.include', {"users.authenticator.username.key": 'key'})
            .and('nested.include', {"users.authenticator.password.name": 'name'})
            .and('nested.include', {"users.authenticator.password.key": 'key'})
        cy.get('@postCluster')
            .its('request.body.spec.replication')
            .should('nested.include', {"role": 'ha'})
            .and('nested.include', {"mode": 'sync'})
            .and('nested.include', {"syncInstances": '2'})
            .and('nested.include', {"groups[0].name": 'group-0'})
            .and('nested.include', {"groups[0].role": 'readonly'})
            .and('nested.include', {"groups[0].instances": '1'})
            .and('nested.include', {"groups[1].name": 'group-1'})
            .and('nested.include', {"groups[1].role": 'none'})
            .and('nested.include', {"groups[1].instances": '1'})
        cy.get('@postCluster')
            .its('request.body.spec.postgresServices')
            .should('nested.include', {"primary.type": 'LoadBalancer'})
            .and('nested.include', {"primary.loadBalancerIP": '1.2.3.4'})
            .and('nested.include', {"primary.customPorts[0].appProtocol": 'protocol'})
            .and('nested.include', {"primary.customPorts[0].name": 'name'})
            .and('nested.include', {"primary.customPorts[0].nodePort": '1234'})
            .and('nested.include', {"primary.customPorts[0].port": '1234'})
            .and('nested.include', {"primary.customPorts[0].protocol": 'UDP'})
            .and('nested.include', {"primary.customPorts[0].targetPort": '1234'})
            .and('nested.include', {"primary.customPorts[1].appProtocol": 'protocol2'})
            .and('nested.include', {"primary.customPorts[1].name": 'name2'})
            .and('nested.include', {"primary.customPorts[1].nodePort": '4321'})
            .and('nested.include', {"primary.customPorts[1].port": '4321'})
            .and('nested.include', {"primary.customPorts[1].protocol": 'SCTP'})
            .and('nested.include', {"primary.customPorts[1].targetPort": '4321'})
            .and('nested.include', {"replicas.type": 'NodePort'})
            .and('nested.include', {"replicas.loadBalancerIP": '1.2.3.4'})
            .and('nested.include', {"replicas.customPorts[0].appProtocol": 'protocol'})
            .and('nested.include', {"replicas.customPorts[0].name": 'name'})
            .and('nested.include', {"replicas.customPorts[0].nodePort": '1234'})
            .and('nested.include', {"replicas.customPorts[0].port": '1234'})
            .and('nested.include', {"replicas.customPorts[0].protocol": 'UDP'})
            .and('nested.include', {"replicas.customPorts[0].targetPort": '1234'})
            .and('nested.include', {"replicas.customPorts[1].appProtocol": 'protocol2'})
            .and('nested.include', {"replicas.customPorts[1].name": 'name2'})
            .and('nested.include', {"replicas.customPorts[1].nodePort": '4321'})
            .and('nested.include', {"replicas.customPorts[1].port": '4321'})
            .and('nested.include', {"replicas.customPorts[1].protocol": 'SCTP'})
            .and('nested.include', {"replicas.customPorts[1].targetPort": '4321'})
        cy.get('@postCluster')
            .its('request.body.spec.metadata')
            .should('nested.include', {"labels.clusterPods.label": 'value'})
            .and('nested.include', {"annotations.allResources.annotation": 'value'})
            .and('nested.include', {"annotations.clusterPods.annotation": 'value'})
            .and('nested.include', {"annotations.services.annotation": 'value'})
            .and('nested.include', {"annotations.primaryService.annotation": 'value'})
            .and('nested.include', {"annotations.replicasService.annotation": 'value'})
        cy.get('@postCluster')
            .its('request.body.spec.pods.scheduling')
            .should('nested.include', {"nodeSelector.key": 'value'})
            .and('nested.include', {"tolerations[0].key": 'key'})
            .and('nested.include', {"tolerations[0].value": 'value'})
            .and('nested.include', {"tolerations[0].operator": 'Equal'})
            .and('nested.include', {"tolerations[0].effect": 'NoSchedule'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].key": 'key'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].operator": 'In'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].values[0]": 'value'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchFields[0].key": 'key'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchFields[0].operator": 'In'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchFields[0].values[0]": 'value'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key": 'key'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator": 'In'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]": 'value'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchFields[0].key": 'key'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchFields[0].operator": 'In'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchFields[0].values[0]": 'value'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight": '10'})
        cy.get('@postCluster')
            .its('request.body.spec.nonProductionOptions.disableClusterPodAntiAffinity')
            .should('eq', true)
        cy.get('@postCluster')
            .its('request.body.spec.nonProductionOptions.disablePatroniResourceRequirements')
            .should('eq', true)
        cy.get('@postCluster')
            .its('request.body.spec.nonProductionOptions.disableClusterResourceRequirements')
            .should('eq', true)
    });

    it('Updating an advanced SGCluster should be possible', () => {
        // Edit advanced cluster
        cy.visit(namespace + '/sgcluster/advanced-' + resourceName + '/edit')
    
        // Advanced options should be enabled
        cy.get('form#createCluster input#advancedMode')
            .should('be.enabled')
      
        // Test Cluster Name
        cy.get('input[data-field="metadata.name"]')
            .should('be.disabled')

        // Test Profile
        cy.get('select[data-field="spec.profile"]')
            .should('have.value', 'testing')    
            .select('development')

        // Test instances
        cy.get('input[data-field="spec.instances"]')
            .should('have.value', '4')
            .clear()
            .type('5')
        
        // Test Volume Size
        cy.get('input[data-field="spec.pods.persistentVolume.size"]')
            .should('have.value', '2')

        // Disable SSL Connections
        cy.get('input[data-field="spec.postgres.ssl.enabled"]')
            .should('be.enabled')
            .click()

        // Test some extensions
        cy.get('form#createCluster li[data-step="extensions"]')
            .click()

        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.db_info"].enableExtension')
            .should('be.enabled')
            .click()
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.pg_repack"].enableExtension')
            .should('be.enabled')
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.plpgsql_check"].enableExtension')
            .should('be.enabled')
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.http"].enableExtension')
            .should('be.enabled')
        cy.get('ul.extensionsList input[data-field="spec.postgres.extensions.hostname"].enableExtension')
            .should('be.enabled')

        // Test managed backups configuration
        cy.get('form#createCluster li[data-step="backups"]')
            .click()

        cy.get('label[data-field="spec.configurations.backups"] > input')
            .should('be.enabled')

        // Storage Details
        cy.get('[data-field="spec.configurations.backups.sgObjectStorage"]')
            .should('have.value', 'storage-' + resourceName)
        
        // Backup Schedule
        cy.get('#backupConfigFullScheduleMin')
            .should('have.value', '1')
            .clear()
            .type('2')
        
        cy.get('#backupConfigFullScheduleHour')
            .should('have.value', '1')
            .clear()    
            .type('2')

        cy.get('#backupConfigFullScheduleDOM')
            .should('have.value', '1')
            .clear()    
            .type('2')
        
        cy.get('#backupConfigFullScheduleMonth')
            .should('have.value', '1')
            .clear()    
            .type('2')

        cy.get('#backupConfigFullScheduleDOW')
            .should('have.value', '1')
            .clear()    
            .type('2')

        cy.get('[data-field="spec.configurations.backups.retention"]')
            .should('have.value', '3')
            .clear()
            .type('2')

        // Base Backup Details
        cy.get('[data-field="spec.configurations.backups.path"]')
            .should('have.value', '/path')
            .clear()
            .type('/new-path')
        
        cy.get('[data-field="spec.configurations.backups.compression"]')
            .should('have.value', 'lzma')
            .select('Brotli')

        //Performance Details
        cy.get('[data-field="spec.configurations.backups.performance.maxNetworkBandwidth"]')
            .should('have.value', '1024')
            .clear()
            .type('2048')

        cy.get('[data-field="spec.configurations.backups.performance.maxDiskBandwidth"]')
            .should('have.value', '1024')
            .clear()
            .type('2048')
        
        cy.get('[data-field="spec.configurations.backups.performance.uploadDiskConcurrency"]')
            .should('have.value', '2')
            .clear()
            .type('1')

        // Test Volume Snapshot
        cy.get('label[data-field="spec.configurations.backups.useVolumeSnapshot"] > input')
            .should('be.enabled')
        
        cy.get('[data-field="spec.configurations.backups.volumeSnapshotClass"]')
            .should('have.value', 'snapshot')
            .clear()
            .type('class')

        cy.get('label[data-field="spec.configurations.backups.fastVolumeSnapshot"] > input')
            .should('be.enabled')
            .click()

        // Test data initialization
        cy.get('form#createCluster li[data-step="initialization"]')
            .click()
        
        // Choose Backup (We're always assuming there's a backup with name "ui-0" on the specified namespace)
        cy.get('input[data-field="spec.initialData.restore.fromBackup"]')
            .should('have.value', 'ui-0')
            .should('be.disabled')

        // Check PITR
        cy.get('input[data-field="spec.initialData.restore.fromBackup.pointInTimeRecovery.restoreToTimestamp"]') 
            .should('be.disabled')
        
        // Performance details
        cy.get('input[data-field="spec.initialData.restore.downloadDiskConcurrency"]')
            .should('have.value', '2') 
            .should('be.disabled')

        // Test User-Supplied Pods Sidecars
        cy.get('form#createCluster li[data-step="sidecars"]')
            .click()

        // Test Custom volumes
        cy.get('input[data-field="spec.pods.customVolumes[0].emptyDir.medium"]')
            .should('have.value', 'medium')
            .clear()
            .type('edit-medium')
        
        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.optional"]')
            .should('be.enabled')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.defaultMode"]')
            .should('have.value', '0')
            .clear()
            .type('1')
        
        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[0].key"]')
            .should('have.value', 'key1')
            .clear()
            .type('edit-1')
        
        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[0].mode"]')
            .should('have.value', '0')
            .clear()
            .type('1')

        cy.get('input[data-field="spec.pods.customVolumes[1].configMap.items[0].path"]')
            .should('have.value', 'path')
            .clear()
            .type('edit-path')
        
        cy.get('input[data-field="spec.pods.customVolumes[2].secret.secretName"]')
            .should('have.value', 'name')
            .clear()
            .type('edit-name')

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.optional"]')
            .should('be.enabled')
            .click()

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.defaultMode"]')
            .should('have.value', '0')
            .clear()
            .type('1')
        
        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[0].key"]')
            .should('have.value', 'key1')
            .clear()
            .type('edit-1')
        
        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[0].mode"]')
            .should('have.value', '0')
            .clear()
            .type('1')

        cy.get('input[data-field="spec.pods.customVolumes[2].secret.items[0].path"]')
            .should('have.value', 'path')
            .clear()
            .type('edit-path')

        // Test Custom Init Containers
        cy.get('input[data-field="spec.pods.customInitContainers[0].name"]')
            .should('have.value', 'container1')
            .clear()
            .type('edit-container1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].image"]')
            .should('have.value', 'image1')
            .clear()
            .type('edit-image1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].imagePullPolicy"]')
            .should('have.value', 'imagePullPolicy1')
            .clear()
            .type('edit-imagePullPolicy1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].workingDir"]')
            .should('have.value', 'workingDir1')
            .clear()
            .type('edit-workingDir1')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].args[0]"]')
            .should('have.value', 'arg1')
            .clear()
            .type('edit-arg1')


        cy.get('input[data-field="spec.pods.customInitContainers[0].args[1]"]')
            .should('have.value', 'arg2')
            .clear()
            .type('edit-arg2')
        

        cy.get('input[data-field="spec.pods.customInitContainers[0].command[0]"]')
            .should('have.value', 'command1')
            .clear()
            .type('edit-command1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].command[1]"]')
            .should('have.value', 'command2')
            .clear()
            .type('edit-command2')

        cy.get('input[data-field="spec.pods.customInitContainers[0].env[0].name"]')
            .should('have.value', 'var1')
            .clear()
            .type('edit-var1')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].env[0].value"]')
            .should('have.value', 'val1')
            .clear()
            .type('edit-val1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].env[1].name"]')
            .should('have.value', 'var2')
            .clear() 
            .type('edit-var2')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].env[1].value"]')
            .should('have.value', 'val2')
            .clear()
            .type('edit-val2')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].name"]')
            .should('have.value', 'port1')
            .clear()
            .type('edit-port1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].hostIP"]')
            .should('have.value', 'ip1')
            .clear()
            .type('edit-ip1')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].hostPort"]')
            .should('have.value', '1')
            .clear()
            .type('11')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[0].containerPort"]')
            .should('have.value', '1')
            .clear()
            .type('11')
        
        cy.get('select[data-field="spec.pods.customInitContainers[0].ports[0].protocol"]')
            .should('have.value', 'TCP')
            .select('SCTP')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].name"]')
            .should('have.value', 'port2')
            .clear()
            .type('edit-port2')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].hostIP"]')
            .should('have.value', 'ip2')
            .clear()
            .type('edit-ip2')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].hostPort"]')
            .should('have.value', '2')
            .clear()
            .type('22')

        cy.get('input[data-field="spec.pods.customInitContainers[0].ports[1].containerPort"]')
            .should('have.value', '2')
            .clear()
            .type('22')
        
        cy.get('select[data-field="spec.pods.customInitContainers[0].ports[1].protocol"]')
            .should('have.value', 'UDP')
            .select('SCTP')

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].name"]')
            .should('have.value', 'vol1')
            .clear()
            .type('edit-vol1')

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].readOnly"]')
            .should('be.enabled')    
            .click()

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].mountPath"]')
            .should('have.value', 'mountPath')
            .clear()
            .type('edit-mountPath')

        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].mountPropagation"]')
            .should('have.value', 'mountPropagation')
            .clear()
            .type('edit-mountPropagation')
        
        cy.get('input[data-field="spec.pods.customInitContainers[0].volumeMounts[0].subPath"]')
            .should('have.value', 'subPath')
            .clear()
            .type('edit-subPath')

        // Test Custom Containers
        cy.get('input[data-field="spec.pods.customContainers[0].name"]')
            .should('have.value', 'container1')
            .clear()
            .type('edit-container1')

        cy.get('input[data-field="spec.pods.customContainers[0].image"]')
            .should('have.value', 'image1')
            .clear()
            .type('edit-image1')

        cy.get('input[data-field="spec.pods.customContainers[0].imagePullPolicy"]')
            .should('have.value', 'imagePullPolicy1')
            .clear()
            .type('edit-imagePullPolicy1')

        cy.get('input[data-field="spec.pods.customContainers[0].workingDir"]')
            .should('have.value', 'workingDir1')
            .clear()
            .type('edit-workingDir1')
        
        cy.get('input[data-field="spec.pods.customContainers[0].args[0]"]')
            .should('have.value', 'arg1')
            .clear()
            .type('edit-arg1')

        cy.get('input[data-field="spec.pods.customContainers[0].args[1]"]')
            .should('have.value', 'arg2')
            .clear()
            .type('edit-arg2')

        cy.get('input[data-field="spec.pods.customContainers[0].command[0]"]')
            .should('have.value', 'command1')
            .clear()
            .type('edit-command1')

        cy.get('input[data-field="spec.pods.customContainers[0].command[1]"]')
            .should('have.value', 'command2')
            .clear()
            .type('edit-command2')

        cy.get('input[data-field="spec.pods.customContainers[0].env[0].name"]')
            .should('have.value', 'var1')
            .clear()
            .type('edit-var1')
        
        cy.get('input[data-field="spec.pods.customContainers[0].env[0].value"]')
            .should('have.value', 'val1')
            .clear()
            .type('edit-val1')

        cy.get('input[data-field="spec.pods.customContainers[0].env[1].name"]')
            .should('have.value', 'var2')
            .clear()
            .type('edit-var2')
        
        cy.get('input[data-field="spec.pods.customContainers[0].env[1].value"]')
            .should('have.value', 'val2')
            .clear()
            .type('edit-val2')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].name"]')
            .should('have.value', 'port1')
            .clear()
            .type('edit-port1')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].hostIP"]')
            .should('have.value', 'ip1')
            .clear()
            .type('edit-ip1')
        
        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].hostPort"]')
            .should('have.value', '1')
            .clear()
            .type('11')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[0].containerPort"]')
            .should('have.value', '1')
            .clear()
            .type('11')
        
        cy.get('select[data-field="spec.pods.customContainers[0].ports[0].protocol"]')
            .should('have.value', 'TCP')
            .select('SCTP')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].name"]')
            .should('have.value', 'port2')
            .clear()
            .type('edit-port2')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].hostIP"]')
            .should('have.value', 'ip2')
            .clear()
            .type('edit-ip2')
        
        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].hostPort"]')
            .should('have.value', '2')
            .clear()
            .type('22')

        cy.get('input[data-field="spec.pods.customContainers[0].ports[1].containerPort"]')
            .should('have.value', '2')
            .clear()
            .type('22')
        
        cy.get('select[data-field="spec.pods.customContainers[0].ports[1].protocol"]')
            .should('have.value', 'UDP')
            .select('SCTP')

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].name"]')
            .should('have.value', 'vol1')
            .clear()
            .type('edit-vol1')

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].readOnly"]')
            .should('be.enabled')    
            .click()

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].mountPath"]')
            .should('have.value', 'mountPath')
            .clear()
            .type('edit-mountPath')

        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].mountPropagation"]')
            .should('have.value', 'mountPropagation')
            .clear()
            .type('edit-mountPropagation')
        
        cy.get('input[data-field="spec.pods.customContainers[0].volumeMounts[0].subPath"]')
            .should('have.value', 'subPath')
            .clear()
            .type('edit-subPath')

        // Test replicate from external instance
        cy.get('form#createCluster li[data-step="replicate-from"]')
        .click()

        cy.get('select[data-field="spec.replicateFrom.source"]') 
            .should('have.value', 'external-storage')
            .select('cluster')

        cy.get('select[data-field="spec.replicateFrom.instance.sgCluster"]')
            .select('rep-sgcluster-' + resourceName)

        // Test scripts
        cy.get('form#createCluster li[data-step="scripts"]')
            .click()
        
        // Test Entry script textarea
        cy.get('textarea[data-field="spec.managedSql.scripts[1].scriptSpec.scripts[0].script"]')
            .should('have.value', '' + resourceName)
            .clear()
            .type('test-' + resourceName)
        
        // Test select script
        cy.get('select[data-field="spec.managedSql.scripts.scriptSource[2]"]')
            .should('have.value', 'script-' + resourceName)        
        cy.get('textarea[data-field="spec.managedSql.scripts[2].scriptSpec.scripts[0].script"]')
            .should('have.value', '' + resourceName)        
            .clear()
            .type('test2-' + resourceName)        
        
        // Test Add Script button
        cy.get('.scriptFieldset > div.fieldsetFooter > a.addRow')
            .click()

        // Test create new script
        cy.get('select[data-field="spec.managedSql.scripts.scriptSource[3]"]')
            .select('createNewScript')

        // Test Entry script textarea
        cy.get('textarea[data-field="spec.managedSql.scripts[3].scriptSpec.scripts[0].script"]')
            .type('test3-' + resourceName)        

        // Test prometheus autobind
        cy.get('form#createCluster li[data-step="sidecars"]')
            .click()

        cy.get('input[data-field="spec.prometheusAutobind"]')
            .should('be.enabled')
            .click()

        // Test Replication
        cy.get('form#createCluster li[data-step="pods-replication"]')
            .click()
        
        cy.get('select[data-field="spec.replication.role"]')
            .should('have.value', 'ha')
            .select('ha-read')
        
        cy.get('select[data-field="spec.replication.mode"]')
            .should('have.value', 'sync')
            .select('strict-sync')

        cy.get('input[data-field="spec.replication.syncInstances"]')
            .should('have.value', '2')
            .clear()
            .type('3')

        cy.get('[data-group="replication-group-0"] input[data-field="spec.replication.groups[0].name"]')
            .should('have.value', 'group-0')
            .clear()
            .type('group-00')
        
        cy.get('[data-group="replication-group-0"] select[data-field="spec.replication.groups[0].role"]')
            .should('have.value', 'readonly')
            .select('ha')
        
        cy.get('[data-group="replication-group-0"] input[data-field="spec.replication.groups[0].instances"]')
            .should('have.value', '1')
            .clear()
            .type('2')
        
        cy.get('[data-add="spec.replication.groups"]')
            .click()

        cy.get('[data-group="replication-group-1"] input[data-field="spec.replication.groups[1].name"]')
            .should('have.value', 'group-1')
            .clear()
            .type('group-01')
        
        cy.get('[data-group="replication-group-1"] select[data-field="spec.replication.groups[1].role"]')
            .should('have.value', 'none')
            .select('readonly')
        
        cy.get('[data-group="replication-group-1"] input[data-field="spec.replication.groups[1].instances"]')
            .should('have.value', '1')
            .clear()
            .type('2')

        // Test Postgres Services
        cy.get('form#createCluster li[data-step="services"]')
            .click()

        cy.get('select[data-field="spec.postgresServices.primary.type"]')
            .should('have.value', 'LoadBalancer')
            .select('NodePort')
        
        cy.get('input[data-field="spec.postgresServices.primary.loadBalancerIP"]')
            .clear()
            .type('4.3.2.1')

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].appProtocol"]')
            .clear()
            .type('edit-protocol')
        
        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].name"]')
            .clear()
            .type('edit-name')
        
        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].nodePort"]')
            .clear()
            .type('4321')

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].port"]')
            .clear()
            .type('4321')

        cy.get('select[data-field="spec.postgresServices.primary.customPorts[1].protocol"]')
            .should('have.value', 'SCTP')
            .select('TCP')

        cy.get('input[data-field="spec.postgresServices.primary.customPorts[1].targetPort"]')
            .clear()
            .type('4321')

        cy.get('fieldset[data-field="spec.postgresServices.primary.customPorts"] .section:first-child a.addRow.delete')
            .click()
        
        cy.get('select[data-field="spec.postgresServices.replicas.type"]')
            .should('have.value', 'NodePort')
            .select('LoadBalancer')
        
        cy.get('input[data-field="spec.postgresServices.replicas.loadBalancerIP"]')
            .clear()
            .type('4.3.2.1')

        cy.get('fieldset[data-field="spec.postgresServices.replicas.customPorts"] .section:last-child a.addRow.delete')
            .click()
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].appProtocol"]')
            .clear()
            .type('edit-protocol')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].name"]')
            .clear()
            .type('edit-name')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].nodePort"]')
            .clear()
            .type('4321')

        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].port"]')
            .clear()
            .type('4321')

        cy.get('select[data-field="spec.postgresServices.replicas.customPorts[0].protocol"]')
            .should('have.value', 'UDP')
            .select('SCTP')
        
        cy.get('input[data-field="spec.postgresServices.replicas.customPorts[0].targetPort"]')
            .clear()
            .type('4321')

        // Test Metadata
        cy.get('form#createCluster li[data-step="metadata"]')
            .click()

        cy.get('input[data-field="spec.metadata.labels.clusterPods[0].label"]')
            .should('have.value', 'label')
            .clear()
            .type('label1')
        cy.get('input[data-field="spec.metadata.labels.clusterPods[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')
        
        cy.get('input[data-field="spec.metadata.annotations.allResources[0].annotation"]')
            .should('have.value', 'annotation')
            .clear()
            .type('annotation1')
        cy.get('input[data-field="spec.metadata.annotations.allResources[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')

        cy.get('input[data-field="spec.metadata.annotations.clusterPods[0].annotation"]')
            .should('have.value', 'annotation')
            .clear()
            .type('annotation1')
        cy.get('input[data-field="spec.metadata.annotations.clusterPods[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')

        cy.get('input[data-field="spec.metadata.annotations.services[0].annotation"]')
            .should('have.value', 'annotation')
            .clear()
            .type('annotation1')
        cy.get('input[data-field="spec.metadata.annotations.services[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')
        
        cy.get('input[data-field="spec.metadata.annotations.primaryService[0].annotation"]')
            .should('have.value', 'annotation')
            .clear()
            .type('annotation1')
        cy.get('input[data-field="spec.metadata.annotations.primaryService[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')
        
        cy.get('input[data-field="spec.metadata.annotations.replicasService[0].annotation"]')
            .should('have.value', 'annotation')
            .clear()
            .type('annotation1')  
        cy.get('input[data-field="spec.metadata.annotations.replicasService[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')

        // Tests Scheduling
        cy.get('form#createCluster li[data-step="scheduling"]')
            .click()

        // Tests Node Selectors
        cy.get('input[data-field="spec.pods.scheduling.nodeSelector[0].label"]')
            .should('have.value', 'key')
            .clear()
            .type('key1')
        cy.get('input[data-field="spec.pods.scheduling.nodeSelector[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')

        // Tests Node Tolerations
        cy.get('input[data-field="spec.pods.scheduling.tolerations[0].key"]')
            .should('have.value', 'key')
            .clear()
            .type('key1')
        cy.get('input[data-field="spec.pods.scheduling.tolerations[0].value"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')
        cy.get('select[data-field="spec.pods.scheduling.tolerations[0].effect"]')
            .should('have.value', 'NoSchedule')
            .select('NoExecute')
        
        // Tests Node Affinity (Required)
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchExpressions.items.properties.key"]')
            .should('have.value', 'key')
            .clear()
            .type('key1')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchExpressions.items.properties.operator"]')
            .should('have.value', 'In')
            .select('NotIn')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchExpressions.items.properties.values"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')

        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchFields.items.properties.key"]')
            .should('have.value', 'key')
            .clear()
            .type('key1')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchFields.items.properties.operator"]')
            .should('have.value', 'In')
            .select('NotIn')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms.items.properties.matchFields.items.properties.values"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')
        
        // Tests Node Affinity (Preferred)
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchExpressions.items.properties.key"]')
            .should('have.value', 'key')
            .clear()
            .type('key1')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchExpressions.items.properties.operator"]')
            .should('have.value', 'In')
            .select('NotIn')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchExpressions.items.properties.values"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')
        
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchFields.items.properties.key"]')
            .should('have.value', 'key')
            .clear()
            .type('key1')
        cy.get('select[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchFields.items.properties.operator"]')
            .should('have.value', 'In')
            .select('NotIn')
        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.items.properties.preference.properties.matchFields.items.properties.values"]')
            .should('have.value', 'value')
            .clear()
            .type('value1')

        cy.get('input[data-field="spec.pods.scheduling.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution.weight"]')
            .should('have.value', '10')
            .clear()
            .type('20')

        // Test Non Production Options
        cy.get('form#createCluster li[data-step="non-production"]')
            .click()

        cy.get('select[data-field="spec.nonProductionOptions.disableClusterPodAntiAffinity"]')
            .should('have.value', 'true')    
            .select('Enable')

        cy.get('select[data-field="spec.nonProductionOptions.disablePatroniResourceRequirements"]')
            .should('have.value', 'true')    
            .select('Enable')

        cy.get('select[data-field="spec.nonProductionOptions.disableClusterResourceRequirements"]')
            .should('have.value', 'true')    
            .select('Enable')

        // Setup get and put mock to check resource is not found and all fields are correctly set
        cy.intercept('GET', '/stackgres/namespaces/' + namespace + '/sgclusters/advanced-' + resourceName,
            (req) => {
                req.continue((res) => {
                    // Adding unknown fields to test they are not overwritten
                    res.body.spec.test = true
                    res.body.spec.postgres.test = true
                    res.body.spec.distributedLogs = {"test": true}
                    res.body.spec.configurations.test = true
                    res.body.spec.pods.test = true
                    res.body.spec.pods.scheduling.test = true
                    res.body.spec.initialData.test = true
                    res.body.spec.initialData.restore.test = true
                    res.body.spec.initialData.restore.fromBackup.test = true
                    res.body.spec.initialData.restore.fromBackup.pointInTimeRecovery.test = true
                    res.body.spec.postgresServices.test = true
                    res.body.spec.postgresServices.primary.test = true
                    res.body.spec.postgresServices.replicas.test = true
                    res.body.spec.metadata.test = true
                    res.body.spec.metadata.labels.test = true
                    res.body.spec.metadata.annotations.test = true
                    res.body.spec.nonProductionOptions.test = true
                })
            })
            .as('getCluster')
        cy.intercept('PUT', '/stackgres/sgclusters',
            (req) => {
              // Check unknown fields were not overwritten
              expect(req.body.spec.test).to.eq(true)
              expect(req.body.spec.postgres.test).to.eq(true)
              expect(req.body.spec.distributedLogs.test).to.eq(true)
              expect(req.body.spec.configurations.test).to.eq(true)
              expect(req.body.spec.pods.test).to.eq(true)
              expect(req.body.spec.pods.scheduling.test).to.eq(true)
              expect(req.body.spec.initialData.test).to.eq(true)
              expect(req.body.spec.initialData.restore.test).to.eq(true)
              expect(req.body.spec.initialData.restore.fromBackup.test).to.eq(true)
              expect(req.body.spec.initialData.restore.fromBackup.pointInTimeRecovery.test).to.eq(true)
              expect(req.body.spec.postgresServices.test).to.eq(true)
              expect(req.body.spec.postgresServices.primary.test).to.eq(true)
              expect(req.body.spec.postgresServices.replicas.test).to.eq(true)
              expect(req.body.spec.metadata.test).to.eq(true)
              expect(req.body.spec.metadata.labels.test).to.eq(true)
              expect(req.body.spec.metadata.annotations.test).to.eq(true)
              expect(req.body.spec.nonProductionOptions.test).to.eq(true)
              // Removing unknown fields since they are unknown to API too
              delete req.body.spec.test
              delete req.body.spec.postgres.test
              delete req.body.spec.distributedLogs
              delete req.body.spec.configurations.test
              delete req.body.spec.pods.test
              delete req.body.spec.initialData.test
              delete req.body.spec.initialData.restore.test
              delete req.body.spec.initialData.restore.fromBackup.test
              delete req.body.spec.initialData.restore.fromBackup.pointInTimeRecovery.test
              delete req.body.spec.postgresServices.test
              delete req.body.spec.postgresServices.primary.test
              delete req.body.spec.postgresServices.replicas.test
              delete req.body.spec.metadata.test
              delete req.body.spec.metadata.labels.test
              delete req.body.spec.metadata.annotations.test
              delete req.body.spec.nonProductionOptions.test
              req.continue();
            })
            .as('putCluster')

        // Test Submit form
        cy.get('form#createCluster button[type="submit"]')
            .click()

        // Test update notification
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Cluster "advanced-' + resourceName + '" updated successfully')
            })

        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgcluster/advanced-' + resourceName)

        // Test data sent to API
        cy.wait('@getCluster')
            .its('response.statusCode')
            .should('eq', 200)
        cy.wait('@putCluster')
            .its('response.statusCode')
            .should('eq', 200)
        cy.get('@putCluster')
            .its('request.body.spec.instances')
            .should('eq', "5")
        cy.get('@putCluster')
            .its('request.body.spec.pods.persistentVolume.size')
            .should('eq', "2Gi")
        cy.get('@putCluster')
            .its('request.body.spec.postgres.ssl')
            .should('be.null')
        cy.get('@putCluster')
            .its('request.body.spec.postgres.extensions')
            .should('have.lengthOf', 4)
            .then((list) => Cypress._.map(list, 'name'))
            .should('include', "pg_repack")
            .and('include', "plpgsql_check")
            .and('include', "http")
            .and('include', "hostname")
        cy.get('@putCluster')
            .its('request.body.spec.configurations.backups')
            .its(0)
            .should('nested.include', {"sgObjectStorage": 'storage-' + resourceName})
            .and('nested.include', {"cronSchedule": "2 2 2 2 2"})
            .and('nested.include', {"retention": "2"})
            .and('nested.include', {"path": "/new-path"})
            .and('nested.include', {"compression": "brotli"})
            .and('nested.include', {"performance.maxNetworkBandwidth": "2048"})
            .and('nested.include', {"performance.maxDiskBandwidth": "2048"})
            .and('nested.include', {"performance.uploadDiskConcurrency": "1"})
            .and('nested.include', {"useVolumeSnapshot": true})
            .and('nested.include', {"volumeSnapshotClass": "class"})
            .and('nested.include', {"fastVolumeSnapshot": false})
        cy.get('@putCluster')
            .its('request.body.spec.initialData.restore')
            .should('nested.include', {"fromBackup.name": "ui-0"})
            .and('nested.include', {"downloadDiskConcurrency": 2})
            .and('have.nested.property', "fromBackup.pointInTimeRecovery.restoreToTimestamp")
        cy.get('@putCluster')
            .its('request.body.spec.pods')
            .should('nested.include', {'customVolumes[0].emptyDir.medium': 'edit-medium'})
            .and('nested.include', {'customVolumes[1].configMap.optional': true})
            .and('nested.include', {'customVolumes[1].configMap.defaultMode': '1'})
            .and('nested.include', {'customVolumes[1].configMap.items[0].key': 'edit-1'})
            .and('nested.include', {'customVolumes[1].configMap.items[0].mode': '1'})
            .and('nested.include', {'customVolumes[1].configMap.items[0].path': 'edit-path'})
            .and('nested.include', {'customVolumes[2].secret.secretName': 'edit-name'})
            .and('nested.include', {'customVolumes[2].secret.optional': true})
            .and('nested.include', {'customVolumes[2].secret.defaultMode': '1'})
            .and('nested.include', {'customVolumes[2].secret.items[0].key': 'edit-1'})
            .and('nested.include', {'customVolumes[2].secret.items[0].mode': '1'})
            .and('nested.include', {'customVolumes[2].secret.items[0].path': 'edit-path'})
            .and('nested.include', {"customInitContainers[0].name": 'edit-container1'})
            .and('nested.include', {"customInitContainers[0].image": 'edit-image1'})
            .and('nested.include', {"customInitContainers[0].imagePullPolicy": 'edit-imagePullPolicy1'})
            .and('nested.include', {"customInitContainers[0].workingDir": 'edit-workingDir1'})
            .and('nested.include', {"customInitContainers[0].args[0]": 'edit-arg1'})
            .and('nested.include', {"customInitContainers[0].args[1]": 'edit-arg2'})
            .and('nested.include', {"customInitContainers[0].command[0]": 'edit-command1'})
            .and('nested.include', {"customInitContainers[0].command[1]": 'edit-command2'})
            .and('nested.include', {"customInitContainers[0].env[0].name": 'edit-var1'})
            .and('nested.include', {"customInitContainers[0].env[0].value": 'edit-val1'})
            .and('nested.include', {"customInitContainers[0].env[1].name": 'edit-var2'})
            .and('nested.include', {"customInitContainers[0].env[1].value": 'edit-val2'})
            .and('nested.include', {"customInitContainers[0].ports[0].name": 'edit-port1'})
            .and('nested.include', {"customInitContainers[0].ports[0].hostIP": 'edit-ip1'})
            .and('nested.include', {"customInitContainers[0].ports[0].hostPort": '11'})
            .and('nested.include', {"customInitContainers[0].ports[0].containerPort": '11'})
            .and('nested.include', {"customInitContainers[0].ports[0].protocol": 'SCTP'})
            .and('nested.include', {"customInitContainers[0].ports[1].name": 'edit-port2'})
            .and('nested.include', {"customInitContainers[0].ports[1].hostIP": 'edit-ip2'})
            .and('nested.include', {"customInitContainers[0].ports[1].hostPort": '22'})
            .and('nested.include', {"customInitContainers[0].ports[1].containerPort": '22'})
            .and('nested.include', {"customInitContainers[0].ports[1].protocol": 'SCTP'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].name": 'edit-vol1'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].readOnly": false})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].mountPath": 'edit-mountPath'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].mountPropagation": 'edit-mountPropagation'})
            .and('nested.include', {"customInitContainers[0].volumeMounts[0].subPath": 'edit-subPath'})
            .and('nested.include', {"customContainers[0].name": 'edit-container1'})
            .and('nested.include', {"customContainers[0].image": 'edit-image1'})
            .and('nested.include', {"customContainers[0].imagePullPolicy": 'edit-imagePullPolicy1'})
            .and('nested.include', {"customContainers[0].workingDir": 'edit-workingDir1'})
            .and('nested.include', {"customContainers[0].args[0]": 'edit-arg1'})
            .and('nested.include', {"customContainers[0].args[1]": 'edit-arg2'})
            .and('nested.include', {"customContainers[0].command[0]": 'edit-command1'})
            .and('nested.include', {"customContainers[0].command[1]": 'edit-command2'})
            .and('nested.include', {"customContainers[0].env[0].name": 'edit-var1'})
            .and('nested.include', {"customContainers[0].env[0].value": 'edit-val1'})
            .and('nested.include', {"customContainers[0].env[1].name": 'edit-var2'})
            .and('nested.include', {"customContainers[0].env[1].value": 'edit-val2'})
            .and('nested.include', {"customContainers[0].ports[0].name": 'edit-port1'})
            .and('nested.include', {"customContainers[0].ports[0].hostIP": 'edit-ip1'})
            .and('nested.include', {"customContainers[0].ports[0].hostPort": '11'})
            .and('nested.include', {"customContainers[0].ports[0].containerPort": '11'})
            .and('nested.include', {"customContainers[0].ports[0].protocol": 'SCTP'})
            .and('nested.include', {"customContainers[0].ports[1].name": 'edit-port2'})
            .and('nested.include', {"customContainers[0].ports[1].hostIP": 'edit-ip2'})
            .and('nested.include', {"customContainers[0].ports[1].hostPort": '22'})
            .and('nested.include', {"customContainers[0].ports[1].containerPort": '22'})
            .and('nested.include', {"customContainers[0].ports[1].protocol": 'SCTP'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].name": 'edit-vol1'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].readOnly": false})
            .and('nested.include', {"customContainers[0].volumeMounts[0].mountPath": 'edit-mountPath'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].mountPropagation": 'edit-mountPropagation'})
            .and('nested.include', {"customContainers[0].volumeMounts[0].subPath": 'edit-subPath'})
        cy.get('@putCluster')
            .its('request.body.spec.replicateFrom')
            .should('nested.include', {"instance.sgCluster": 'rep-sgcluster-' + resourceName})
        cy.get('@putCluster')
            .its('request.body.spec.managedSql')
            .should('nested.include', {"scripts[1].scriptSpec.scripts[0].script": 'test-' + resourceName})
            .and('nested.include', {"scripts[2].sgScript": 'script-' + resourceName})
            .and('nested.include', {"scripts[2].scriptSpec.scripts[0].script": 'test2-' + resourceName})
            .and('nested.include', {"scripts[3].scriptSpec.scripts[0].script": 'test3-' + resourceName})
        cy.get('@putCluster')
            .its('request.body.spec.prometheusAutobind')
            .should('eq', false)
        cy.get('@putCluster')
            .its('request.body.spec.replication')
            .should('nested.include', {"role": 'ha-read'})
            .and('nested.include', {"mode": 'strict-sync'})
            .and('nested.include', {"syncInstances": '3'})
            .and('nested.include', {"groups[0].name": 'group-00'})
            .and('nested.include', {"groups[0].role": 'ha'})
            .and('nested.include', {"groups[0].instances": '2'})
            .and('nested.include', {"groups[1].name": 'group-01'})
            .and('nested.include', {"groups[1].role": 'readonly'})
            .and('nested.include', {"groups[1].instances": '2'})
        cy.get('@putCluster')
            .its('request.body.spec.postgresServices')
            .should('nested.include', {"primary.type": 'NodePort'})
            .and('nested.include', {"primary.loadBalancerIP": '4.3.2.1'})
            .and('nested.include', {"primary.customPorts[0].appProtocol": 'edit-protocol'})
            .and('nested.include', {"primary.customPorts[0].name": 'edit-name'})
            .and('nested.include', {"primary.customPorts[0].nodePort": '4321'})
            .and('nested.include', {"primary.customPorts[0].port": '4321'})
            .and('nested.include', {"primary.customPorts[0].protocol": 'TCP'})
            .and('nested.include', {"primary.customPorts[0].targetPort": '4321'})
            .and('nested.include', {"replicas.type": 'LoadBalancer'})
            .and('nested.include', {"replicas.loadBalancerIP": '4.3.2.1'})
            .and('nested.include', {"replicas.customPorts[0].appProtocol": 'edit-protocol'})
            .and('nested.include', {"replicas.customPorts[0].name": 'edit-name'})
            .and('nested.include', {"replicas.customPorts[0].nodePort": '4321'})
            .and('nested.include', {"replicas.customPorts[0].port": '4321'})
            .and('nested.include', {"replicas.customPorts[0].protocol": 'SCTP'})
            .and('nested.include', {"replicas.customPorts[0].targetPort": '4321'})
        cy.get('@putCluster')
            .its('request.body.spec.metadata')
            .should('nested.include', {"labels.clusterPods.label1": 'value1'})
            .and('nested.include', {"annotations.allResources.annotation1": 'value1'})
            .and('nested.include', {"annotations.clusterPods.annotation1": 'value1'})
            .and('nested.include', {"annotations.services.annotation1": 'value1'})
            .and('nested.include', {"annotations.primaryService.annotation1": 'value1'})
            .and('nested.include', {"annotations.replicasService.annotation1": 'value1'})
        cy.get('@putCluster')
            .its('request.body.spec.pods.scheduling')
            .should('nested.include', {"nodeSelector.key1": 'value1'})
            .and('nested.include', {"tolerations[0].key": 'key1'})
            .and('nested.include', {"tolerations[0].value": 'value1'})
            .and('nested.include', {"tolerations[0].operator": 'Equal'})
            .and('nested.include', {"tolerations[0].effect": 'NoExecute'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].key": 'key1'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].operator": 'NotIn'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].values[0]": 'value1'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchFields[0].key": 'key1'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchFields[0].operator": 'NotIn'})
            .and('nested.include', {"nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchFields[0].values[0]": 'value1'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key": 'key1'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator": 'NotIn'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]": 'value1'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchFields[0].key": 'key1'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchFields[0].operator": 'NotIn'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchFields[0].values[0]": 'value1'})
            .and('nested.include', {"nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight": '20'})
        cy.get('@putCluster')
            .its('request.body.spec.nonProductionOptions.disableClusterPodAntiAffinity')
            .should('eq', false)
        cy.get('@putCluster')
            .its('request.body.spec.nonProductionOptions.disablePatroniResourceRequirements')
            .should('eq', false)
        cy.get('@putCluster')
            .its('request.body.spec.nonProductionOptions.disableClusterResourceRequirements')
            .should('eq', false)
    }); 

    it('Repeater fields should match error responses coming from the API', () => {
        // Choose custom wizard
        cy.get('[data-field="formTemplate.custom"]')
            .click()

        // Enable advanced options
        cy.get('form#createCluster input#advancedMode')
            .click()
        
        // Test Cluster Name
        cy.get('input[data-field="metadata.name"]')
            .type('repeater-' + resourceName)
        
        // Tests Node Tolerations repeaters
        cy.get('form#createCluster li[data-step="scheduling"]')
            .click()
            
        cy.get('div.scheduling.repeater.tolerations div.fieldsetFooter > a.addRow')
            .click()

        cy.get('input[data-field="spec.pods.scheduling.tolerations[0].value"]')
            .type('value')

        // Test Submit form
        cy.get('form#createCluster button[type="submit"]')
            .click()
        
        cy.get('input[data-field="spec.pods.scheduling.tolerations[0].key"]')
            .should('have.class', 'notValid')
    });

    it('Enable Monitoring to enable Metrics Exporter and Prometheus Autobind ', () => {
        // Choose custom wizard
        cy.get('[data-field="formTemplate.custom"]')
            .click()

        // Enable advanced options
        cy.get('input#advancedMode')
            .click()

        //If Monitoring is ON, Metrics Exporter and Prometheus Autobind should be ON
        cy.get('input#enableMonitoring')
            .click()

        cy.get('form#createCluster li[data-step="sidecars"]')
            .click()

        cy.get('input#metricsExporter')
            .should('be.checked')

        cy.get('input#prometheusAutobind')
            .should('be.checked')

        //If Metrics Exporter is OFF, Monitoring should be OFF
        cy.get('input#metricsExporter')
            .click()

        cy.get('form#createCluster li[data-step="cluster"]')
            .click()

        cy.get('input#enableMonitoring')
            .should('not.be.checked')

        //If Monitoring is switched OFF from ON state, Metrics Exporter and Prometheus Autobind should return to their default states (ME: ON, PA: OFF)
        cy.get('input#enableMonitoring')
            .click()
            .click()

        cy.get('form#createCluster li[data-step="sidecars"]')
            .click()

        cy.get('input#metricsExporter')
            .should('be.checked')

        cy.get('input#prometheusAutobind')
            .should('not.be.checked')

    });

})
