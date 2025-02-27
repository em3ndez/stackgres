describe('Create SGDbOp', () => {
    Cypress.on('uncaught:exception', (err, runnable) => {
      return false
    });

    const namespace = Cypress.env('k8s_namespace')
    let resourceName;
    let clusterName;
    let pgConfigName;

    before( () => {
        cy.login()

        resourceName = Cypress._.random(0, 1e6);
        clusterName = 'cluster-' + resourceName;
        pgConfigName = 'pgconfig-' + resourceName;

        cy.createCRD('sgclusters', {
            metadata: {
                name: clusterName,
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
                    version: "14.4",
                    /*extensions: [{
                        name: "pg_repack",
                        version: "1.4.7",
                        publisher: "com.ongres",
                        repository: "https://extensions.stackgres.io/postgres/repository"
                    }],*/
                    flavor: "vanilla"
                }
            }  
        });

        cy.createCRD('sgpgconfigs', {
            metadata: {
                name: pgConfigName,
                namespace: namespace
            },
            spec: {
                postgresVersion: "15",
                'postgresql.conf':""
            }
        })
    });

    beforeEach( () => {
      cy.gc()
      cy.login()
      cy.setCookie('sgReload', '0')
      cy.setCookie('sgTimezone', 'utc')
      cy.visit(namespace + '/sgdbops/new')
    });

    after( () => {
        cy.login()

        cy.deleteCluster(namespace, clusterName);

        cy.deleteCRD('sgpgconfigs', {
            metadata: {
                name: pgConfigName,
                namespace: namespace
            }
        });

        cy.deleteCRD('sgdbops', {
            metadata: {
                name: 'benchmark-' + resourceName,
                namespace: namespace
            }
        });

        cy.deleteCRD('sgdbops', {
            metadata: {
                name: 'vacuum-' + resourceName,
                namespace: namespace
            }
        });

        cy.deleteCRD('sgdbops', {
            metadata: {
                name: 'sec-upg-' + resourceName,
                namespace: namespace
            }
        });

        cy.deleteCRD('sgdbops', {
            metadata: {
                name: 'minor-upg-' + resourceName,
                namespace: namespace
            }
        });

        cy.deleteCRD('sgdbops', {
            metadata: {
                name: 'major-upg-' + resourceName,
                namespace: namespace
            }
        });

        cy.deleteCRD('sgdbops', {
            metadata: {
                name: 'restart-' + resourceName,
                namespace: namespace
            }
        });

        /*cy.deleteCRD('sgdbops', {
            metadata: {
                name: 'repack-' + resourceName,
                namespace: namespace
            }
        });*/
    });

    it('Create SGDbOps form should be visible', () => {
        cy.get('form#createDbops')
            .should('be.visible')
    });  

    it('Creating a Benchmark SGDbOps should be possible', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('benchmark-' + resourceName)

        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)
        
        // Test Benchmark input
        cy.get('label[for="benchmark"]')
            .click()

        // Test Benchmark input
        cy.get('label[for="benchmark"]')
            .click()

        // Test runAt
        cy.get('input[data-field="spec.runAt"]')
            .type('9999-01-01 00:00:00')
        cy.get('.daterangepicker button.applyBtn')
            .click()

        // Test timeout
        cy.get('select[data-field="spec.timeout.days"]')
         .select('10')
        cy.get('select[data-field="spec.timeout.hours"]')
         .select('20')
        cy.get('select[data-field="spec.timeout.minutes"]')
         .select('30')
        cy.get('select[data-field="spec.timeout.seconds"]')
         .select('40')

        // Test max retries
        cy.get('select[data-field="spec.maxRetries"]')
         .select('10')

        // Test Benchmark specs
        cy.get('select[data-field="spec.benchmark.connectionType"]')
            .select('replicas-service')

        cy.get('input[data-field="spec.benchmark.pgbench.databaseSize"]')
            .type('100')
        
        cy.get('select[data-field="spec.benchmark.pgbench.databaseSize"]')
            .select('Mi')

        cy.get('label[data-field="spec.benchmark.pgbench.usePreparedStatements"]')
            .click()

        cy.get('input[data-field="spec.benchmark.pgbench.concurrentClients"]')
            .clear()
            .type('10')
        
        cy.get('input[data-field="spec.benchmark.pgbench.threads"]')
            .clear()
            .type('10')

        cy.get('select[data-field="spec.benchmark.pgbench.duration.days"]')
            .select('10')
        cy.get('select[data-field="spec.benchmark.pgbench.duration.hours"]')
            .select('20')
        cy.get('select[data-field="spec.benchmark.pgbench.duration.minutes"]')
            .select('30')
        cy.get('select[data-field="spec.benchmark.pgbench.duration.seconds"]')
            .select('40')

        // Test Dry Run
        cy.get('form#createDbops button[data-field="dryRun"]')
            .click()

        cy.get('#crdSummary')
            .should('be.visible')

        cy.get('#crdSummary span.close')
            .click()
        
        cy.get('#crdSummary')
            .should('not.exist')

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Database operation "benchmark-' + resourceName + '" created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    }); 

    it('Creating a Vacuum SGDbOps should be possible', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('vacuum-' + resourceName)

        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        // Test Vaccum input
        cy.get('label[for="vacuum"]')
            .click()

        // Test runAt
        cy.get('input[data-field="spec.runAt"]')
            .type('9999-01-01 00:00:00')
        cy.get('.daterangepicker button.applyBtn')
            .click()

        // Test Vaccum specs
        cy.get('label[data-field="spec.vacuum.full"]')
            .click()
        cy.get('label[data-field="spec.vacuum.freeze"]')
            .click()
        cy.get('label[data-field="spec.vacuum.analyze"]')
            .click()
        cy.get('label[data-field="spec.vacuum.disablePageSkipping"]')
            .click()
        
        // Test per-database Vacuum specs
        cy.get('label[data-field="spec.vacuum.databases"]')
            .click()

        cy.get('input[data-field="spec.vacuum.databases[0].name"]')
            .type(resourceName)

        cy.get('select[data-field="spec.vacuum.databases[0].full"]')
            .select('false')
        cy.get('select[data-field="spec.vacuum.databases[0].freeze"]')
            .select('false')
        cy.get('select[data-field="spec.vacuum.databases[0].analyze"]')
            .select('true')
        cy.get('select[data-field="spec.vacuum.databases[0].disablePageSkipping"]')
            .select('true')

        // Test Dry Run
        cy.get('form#createDbops button[data-field="dryRun"]')
            .click()

        cy.get('#crdSummary')
            .should('be.visible')

        cy.get('#crdSummary span.close')
            .click()
        
        cy.get('#crdSummary')
            .should('not.exist')

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Database operation "vacuum-' + resourceName + '" created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    });

    it('Creating a Security Upgrade SGDbOps should be possible', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('sec-upg-' + resourceName)

        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        // Test Security Upgrade input
        cy.get('label[for="securityUpgrade"]')
            .click()

        // Test runAt
        cy.get('input[data-field="spec.runAt"]')
            .type('9999-01-01 00:00:00')
        cy.get('.daterangepicker button.applyBtn')
            .click()

        // Test Security Upgrade specs
        cy.get('select[data-field="spec.securityUpgrade.method"]')
            .select('ReducedImpact')

        // Test Dry Run
        cy.get('form#createDbops button[data-field="dryRun"]')
            .click()

        cy.get('#crdSummary')
            .should('be.visible')

        cy.get('#crdSummary span.close')
            .click()
        
        cy.get('#crdSummary')
            .should('not.exist')

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Database operation "sec-upg-' + resourceName + '" created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    });

    it('Creating a Minor Version Upgrade SGDbOps should be possible', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('minor-upg-' + resourceName)

        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)
        
        // Test Minor Version Upgrade input
        cy.get('label[for="minorVersionUpgrade"]')
            .click()
    
        // Test runAt
        cy.get('input[data-field="spec.runAt"]')
            .type('9999-01-01 00:00:00')
        cy.get('.daterangepicker button.applyBtn')
            .click()

        // Test Minor Version Upgrade specs
        cy.get('select[data-field="spec.minorVersionUpgrade.method"]')
            .select('ReducedImpact')

        cy.get('select[data-field="spec.minorVersionUpgrade.postgresVersion"]')
            .select('14.5')

        // Test Dry Run
        cy.get('form#createDbops button[data-field="dryRun"]')
            .click()

        cy.get('#crdSummary')
            .should('be.visible')

        cy.get('#crdSummary span.close')
            .click()
        
        cy.get('#crdSummary')
            .should('not.exist')

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Database operation "minor-upg-' + resourceName + '" created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    });
   
    it('Creating a Major Version Upgrade SGDbOps should be possible', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('major-upg-' + resourceName)

        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        // Test Minor Version Upgrade input
        cy.get('label[for="majorVersionUpgrade"]')
            .click()

        // Test runAt
        cy.get('input[data-field="spec.runAt"]')
            .type('9999-01-01 00:00:00')
        cy.get('.daterangepicker button.applyBtn')
            .click()

        // Test Minor Version Upgrade specs
        cy.get('label[data-field="spec.majorVersionUpgrade.link"]')
            .click()
        
        cy.get('label[data-field="spec.majorVersionUpgrade.check"]')
            .click()

        cy.get('input[data-field="spec.majorVersionUpgrade.backupPath"]')
            .clear()
            .type('/backup/path/test')

        cy.get('select[data-field="spec.majorVersionUpgrade.postgresVersion"]')
            .select('15.2')

        cy.get('select[data-field="spec.majorVersionUpgrade.sgPostgresConfig"]')
            .select(pgConfigName)

        // Test Dry Run
        cy.get('form#createDbops button[data-field="dryRun"]')
            .click()

        cy.get('#crdSummary')
            .should('be.visible')

        cy.get('#crdSummary span.close')
            .click()
        
        cy.get('#crdSummary')
            .should('not.exist')

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Database operation "major-upg-' + resourceName + '" created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    });

    it('Creating a Restart SGDbOps should be possible', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('restart-' + resourceName)

        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        // Test Restart input
        cy.get('label[for="restart"]')
            .click()

        // Test runAt
        cy.get('input[data-field="spec.runAt"]')
            .type('9999-01-01 00:00:00')    
        cy.get('.daterangepicker button.applyBtn')
            .click()

        // Test Restart specs
        cy.get('select[data-field="spec.restart.method"]')
            .select('ReducedImpact')

        cy.get('label[data-field="spec.restart.onlyPendingRestart"]')
            .click()

        // Test Dry Run
        cy.get('form#createDbops button[data-field="dryRun"]')
            .click()

        cy.get('#crdSummary')
            .should('be.visible')

        cy.get('#crdSummary span.close')
            .click()
        
        cy.get('#crdSummary')
            .should('not.exist')

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Database operation "restart-' + resourceName + '" created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    });

    it('Creating a SGDbOps with a default name should be possible', () => {
        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        // Test Restart input
        cy.get('label[for="restart"]')
            .click()

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')

    });
  
    /*it('Creating a Repack SGDbOps should be possible', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('repack-' + resourceName)

        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        // Test Repack input
        cy.get('label[for="repack"]')
            .click()

        // Test runAt
        cy.get('input[data-field="spec.runAt"]')
            .type('9999-01-01 00:00:00')
        cy.get('.daterangepicker button.applyBtn')
            .click()

        // Test Repack specs
        cy.get('label[data-field="spec.repack.noOrder"]')
            .click()
        cy.get('label[data-field="spec.repack.noKillBackend"]')
            .click()
        cy.get('label[data-field="spec.repack.noAnalyze"]')
            .click()
        cy.get('label[data-field="spec.repack.excludeExtension"]')
            .click()

        cy.get('select[data-field="spec.repack.waitTimeout.days"]')
            .select('10')
        cy.get('select[data-field="spec.repack.waitTimeout.hours"]')
            .select('20')
        cy.get('select[data-field="spec.repack.waitTimeout.minutes"]')
            .select('30')
        cy.get('select[data-field="spec.repack.waitTimeout.seconds"]')
            .select('40')
        
        // Test per-database Repack specs
        cy.get('label[data-field="spec.repack.databases"]')
            .click()

        cy.get('input[data-field="spec.repack.databases[0].name"]')
            .type(resourceName)

        cy.get('select[data-field="spec.repack.databases[0].noOrder"]')
            .select('false')
        cy.get('select[data-field="spec.repack.databases[0].noKillBackend"]')
            .select('false')
        cy.get('select[data-field="spec.repack.databases[0].noAnalyze"]')
            .select('false')
        cy.get('select[data-field="spec.repack.databases[0].excludeExtension"]')
            .select('false')
        
        cy.get('select[data-field="spec.repack.databases[0].waitTimeout"]')
            .select('false')
        cy.get('select[data-field="spec.repack.databases[0].waitTimeout.days"]')
            .select('10')
        cy.get('select[data-field="spec.repack.databases[0].waitTimeout.hours"]')
            .select('20')
        cy.get('select[data-field="spec.repack.databases[0].waitTimeout.minutes"]')
            .select('30')
        cy.get('select[data-field="spec.repack.databases[0].waitTimeout.seconds"]')
            .select('40')

        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Database operation "repack-' + resourceName + '" created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    });*/

    it('notValid class should be added if no op is selected and removed once op is selected', () => {
        // Test Op Name
        cy.get('[data-field="metadata.name"]')
            .clear()
            .type('not-valid-' + resourceName)
        
        // Submit form without selecting an op
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        cy.get('form#createDbops button[type="submit"]')
            .click()

        // Error notification should appear
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Please fill every mandatory field in the form')
        })

        // notValid class should be added to op boxes
        cy.get('.optionBoxes label')
            .should('have.class', 'notValid')

        // notValid class should be removed when op is selected
        cy.get('label[for="restart"]')
            .click()

        cy.get('.optionBoxes label')
            .should('not.have.class', 'notValid')
    });

    it('Creating a SGDbOps with tolerations should be pssible', () => {
        // Test target SGCluster
        cy.get('select[data-field="spec.sgCluster"]')
            .select(clusterName)

        // Test Restart input
        cy.get('label[for="restart"]')
            .click()
        
        // Tests Node Tolerations
        cy.get('input[data-field="spec.scheduling.tolerations[0].key"]')
            .type('key')
        
        cy.get('input[data-field="spec.scheduling.tolerations[0].value"]')
            .type('value')
    
        cy.get('select[data-field="spec.scheduling.tolerations[0].effect"]')
            .select('NoSchedule')
        
        // Test Submit form
        cy.get('form#createDbops button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('created successfully')
            })
        
        // Test user redirection
        cy.location('pathname').should('eq', '/admin/' + namespace + '/sgdbops')
    });
    
  })
